package ggp.apollo.stats;

import ggp.apollo.CondensedMatch;
import ggp.apollo.Game;
import ggp.apollo.Persistence;
import ggp.apollo.Player;
import ggp.apollo.StoredStatistics;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.jdo.JDOObjectNotFoundException;
import javax.jdo.PersistenceManager;

import org.datanucleus.store.query.AbstractQueryResult;

import com.google.appengine.repackaged.org.json.JSONArray;
import com.google.appengine.repackaged.org.json.JSONException;
import com.google.appengine.repackaged.org.json.JSONObject;

public class Statistics {
    public static void computeStatistics() throws IOException {
        int nMatches = 0;
        int nMatchesFinished = 0;
        int nMatchesAbandoned = 0;
        int nMatchesStatErrors = 0;
        int nMatchesInPastHour = 0;
        int nMatchesInPastDay = 0;        
        
        MedianPerDay matchesPerDay = new MedianPerDay();
        Map<String,MedianPerDay> playerErrorsPerDay = new HashMap<String,MedianPerDay>();  
        Map<String,WeightedAverage> playerAverageScore = new HashMap<String,WeightedAverage>();
        Map<String,WeightedAverage> playerDecayedAverageScore = new HashMap<String,WeightedAverage>();
        Map<String,Map<String,WeightedAverage>> averageScoreVersus = new HashMap<String,Map<String,WeightedAverage>>();
        Map<String,WeightedAverage> gameAverageMoves = new HashMap<String,WeightedAverage>();
        
        WeightedAverage playersPerMatch = new WeightedAverage();
        WeightedAverage movesPerMatch = new WeightedAverage();

        Set<String> toPurge = new HashSet<String>();
        
        long nComputeBeganAt = System.currentTimeMillis();
        PersistenceManager pm = Persistence.getPersistenceManager();
        try {
            Iterator<?> sqr = ((AbstractQueryResult) pm.newQuery(CondensedMatch.class).execute()).iterator();
            while (sqr.hasNext()) {
                CondensedMatch c = (CondensedMatch)sqr.next();
                if (!c.isReady()) continue;
                JSONObject theJSON = c.getCondensedJSON();
                                
                nMatches++;
                try {
                    // Check whether this match needs to be purged from the Apollo server's
                    // condensed match cache. This should very rarely need to be used: it is
                    // a safety mechanism in case bad data gets into the cache and needs to
                    // be cleared out.
                    if (matchRequiringPurging(theJSON)) {
                        toPurge.add(c.getSpectatorURL());
                        continue;
                    }
                    
                    if (theJSON.getBoolean("isCompleted")) {
                        nMatchesFinished++;                        
                        movesPerMatch.addValue(theJSON.getInt("moveCount"));
                        
                        String theGame = theJSON.getString("gameMetaURL");
                        if (!gameAverageMoves.containsKey(theGame)) {
                            gameAverageMoves.put(theGame, new WeightedAverage());
                        }
                        gameAverageMoves.get(theGame).addValue(theJSON.getInt("moveCount"));                        
                        
                        // Score-related statistics.
                        for (int i = 0; i < c.getPlayers().size(); i++) {
                            String aPlayer = c.getPlayers().get(i);
                            int aPlayerScore = theJSON.getJSONArray("goalValues").getInt(i);
                            
                            if (!playerAverageScore.containsKey(aPlayer)) {
                                playerAverageScore.put(aPlayer, new WeightedAverage());
                            }
                            playerAverageScore.get(aPlayer).addValue(aPlayerScore);
                            
                            double ageInDays = (double)(System.currentTimeMillis() - theJSON.getLong("startTime")) / (double)(86400000L);
                            if (!playerDecayedAverageScore.containsKey(aPlayer)) {
                                playerDecayedAverageScore.put(aPlayer, new WeightedAverage());
                            }
                            playerDecayedAverageScore.get(aPlayer).addValue(aPlayerScore, Math.pow(0.98, ageInDays));
                            
                            if (!playerErrorsPerDay.containsKey(aPlayer)) {
                                playerErrorsPerDay.put(aPlayer, new MedianPerDay());
                            }
                            int nErrors = 0;
                            if (theJSON.has("errors")) {
                                JSONArray theErrors = theJSON.getJSONArray("errors");
                                for (int j = 0; j < theErrors.length(); j++) {
                                    if (!theErrors.getJSONArray(j).getString(i).isEmpty()) {
                                        nErrors++;
                                    }
                                }
                            }
                            playerErrorsPerDay.get(aPlayer).addToDay(nErrors, theJSON.getLong("startTime"));
                            
                            for (String bPlayer : c.getPlayers()) {
                                if (bPlayer.equals(aPlayer))
                                    continue;
                                if (!averageScoreVersus.containsKey(aPlayer)) {
                                    averageScoreVersus.put(aPlayer, new HashMap<String,WeightedAverage>());
                                }
                                if (!averageScoreVersus.get(aPlayer).containsKey(bPlayer)) {
                                    averageScoreVersus.get(aPlayer).put(bPlayer, new WeightedAverage());
                                }
                                averageScoreVersus.get(aPlayer).get(bPlayer).addValue(aPlayerScore);
                            }
                        }
                    } else {
                        nMatchesAbandoned++;
                    }
                    
                    playersPerMatch.addValue(theJSON.getJSONArray("gameRoleNames").length());
                    
                    if (System.currentTimeMillis() - theJSON.getLong("startTime") < 3600000L) nMatchesInPastHour++;
                    if (System.currentTimeMillis() - theJSON.getLong("startTime") < 86400000L) nMatchesInPastDay++;
                                        
                    matchesPerDay.addToDay(1, theJSON.getLong("startTime"));
                } catch(JSONException ex) {
                    nMatchesStatErrors++;
                    throw new RuntimeException(ex);
                }
            }
        } catch(JDOObjectNotFoundException e) {
            throw new RuntimeException(e);
        } finally {
            pm.close();
        }
        long nComputeTime = System.currentTimeMillis() - nComputeBeganAt;
        
        for (String matchKey : toPurge) {
            Persistence.clearSpecific(matchKey, CondensedMatch.class);
        }
        
        // Store the statistics as a JSON object in the datastore.
        try {
            JSONObject overall = new JSONObject();
            Map<String, JSONObject> perPlayer = new HashMap<String, JSONObject>();
            Map<String, JSONObject> perGame = new HashMap<String, JSONObject>();
            
            // Store the overall statistics
            overall.put("matches", nMatches);
            overall.put("matchesFinished", nMatchesFinished);
            overall.put("matchesAbandoned", nMatchesAbandoned);
            overall.put("matchesAverageMoves", movesPerMatch.getWeightedAverage());
            overall.put("matchesAveragePlayers", playersPerMatch.getWeightedAverage());
            overall.put("matchesStatErrors", nMatchesStatErrors);
            overall.put("computeTime", nComputeTime);
            overall.put("leaderboard", playerAverageScore);
            overall.put("decayedLeaderboard", playerDecayedAverageScore);
            overall.put("computedAt", System.currentTimeMillis());
            overall.put("matchesInPastHour", nMatchesInPastHour);
            overall.put("matchesInPastDay", nMatchesInPastDay);            
            overall.put("matchesPerDayMedian", matchesPerDay.getMedianPerDay());

            // Store the per-player statistics
            for (Player p : Player.loadPlayers()) {
                String playerName = p.getName();
                perPlayer.put(playerName, new JSONObject());
                
                perPlayer.get(playerName).put("averageScore", playerAverageScore.get(playerName));                
                perPlayer.get(playerName).put("decayedAverageScore", playerDecayedAverageScore.get(playerName));
                perPlayer.get(playerName).put("averageScoreVersus", averageScoreVersus.get(playerName));
                perPlayer.get(playerName).put("medianErrorsPerDay", playerErrorsPerDay.get(playerName));
            }
            
            // Store the per-game statistics
            for (Game g : Game.loadGames()) {
                String gameName = g.getMetaURL();
                perGame.put(gameName, new JSONObject());
                
                perGame.get(gameName).put("averageMoves", gameAverageMoves.get(gameName));
            }
            
            StoredStatistics s = new StoredStatistics();
            s.setOverallStats(overall);
            for (String playerName : perPlayer.keySet()) {
                s.setPlayerStats(playerName, perPlayer.get(playerName));
            }
            for (String gameName : perGame.keySet()) {
                s.setGameStats(gameName, perGame.get(gameName));
            }
            s.save();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
    
    // Determine whether a match should be purged from the Apollo server's condensed
    // match cache during the next batch statistics computation run.
    // TODO: Migrate this to be independent of the statistics-generating batch routine.
    public static boolean matchRequiringPurging(JSONObject theJSON) throws JSONException {
        return false;
    }
}