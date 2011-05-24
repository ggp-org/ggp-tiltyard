package ggp.apollo.stats;

import ggp.apollo.CondensedMatch;
import ggp.apollo.Game;
import ggp.apollo.Persistence;
import ggp.apollo.Player;
import ggp.apollo.StoredStatistics;
import ggp.apollo.stats.counters.EloRank;
import ggp.apollo.stats.counters.MedianPerDay;
import ggp.apollo.stats.counters.WeightedAverage;
import ggp.apollo.stats.counters.WinLossCounter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.jdo.JDOObjectNotFoundException;
import javax.jdo.PersistenceManager;
import javax.jdo.Query;

import org.datanucleus.store.query.AbstractQueryResult;

import com.google.appengine.repackaged.org.json.JSONArray;
import com.google.appengine.repackaged.org.json.JSONException;
import com.google.appengine.repackaged.org.json.JSONObject;

public class Statistics {
    public static final int STATS_VERSION = 33;

    static final String[] dummyArray = new String[] {};
    static class SortableTinyMatch implements Comparable<SortableTinyMatch> {
        private static List<String> thePlayerNames;
        private long startTime;
        private int[] thePlayers;
        private int[] theGoalValues;
        private String theGameURL;
        
        public SortableTinyMatch(List<String> thePlayers, JSONObject theJSON) throws JSONException {
            this.theGameURL = theJSON.getString("gameMetaURL");
            this.startTime = theJSON.getLong("startTime");
            
            this.theGoalValues = new int[theJSON.getJSONArray("goalValues").length()];
            for (int i = 0; i < this.theGoalValues.length; i++) {
                this.theGoalValues[i] = theJSON.getJSONArray("goalValues").getInt(i);
            }
                        
            if (thePlayerNames == null) {
                thePlayerNames = new ArrayList<String>();                
            }
            this.thePlayers = new int[thePlayers.size()];
            for (int i = 0; i < thePlayers.size(); i++) {
                int nIndex = thePlayerNames.indexOf(thePlayers.get(i));
                if (nIndex == -1) {
                    thePlayerNames.add(thePlayers.get(i));
                    nIndex = thePlayerNames.indexOf(thePlayers.get(i));
                }
                this.thePlayers[i] = nIndex;
            }
        }
        public int compareTo(SortableTinyMatch k2) {
            if (k2.startTime > startTime) return -1;
            if (k2.startTime < startTime) return 1;
            return this.hashCode() > k2.hashCode() ? 1 : -1;
        }
        
        public String[] getPlayers() {
            String[] playerNames = new String[thePlayers.length];
            for (int i = 0; i < thePlayers.length; i++) {
                playerNames[i] = thePlayerNames.get(thePlayers[i]);
            }
            return playerNames;
        }
        public int[] getGoalValues() {
            return theGoalValues;
        }
        public String getGameURL() {
            return theGameURL;
        }
    }
    
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
        Map<String,Map<String,WeightedAverage>> averageScoreOn = new HashMap<String,Map<String,WeightedAverage>>();
        Map<String,Map<String,WeightedAverage>> averageScoreVersus = new HashMap<String,Map<String,WeightedAverage>>();
        Map<String,WeightedAverage> gameAverageMoves = new HashMap<String,WeightedAverage>();
        Map<String,Map<String,Map<String,WinLossCounter>>> playerWinsVersusPlayerOnGame = new HashMap<String,Map<String,Map<String,WinLossCounter>>>();
        Map<String,Double> netScores = new HashMap<String,Double>();
        
        WeightedAverage playersPerMatch = new WeightedAverage();
        WeightedAverage movesPerMatch = new WeightedAverage();
        
        Set<String> toPurge = new HashSet<String>();
        
        SortedSet<SortableTinyMatch> theKeySet = new TreeSet<SortableTinyMatch>();

        /* Compute most statistics in the initial unsorted first pass over the match data */
        long nComputeBeganAt = System.currentTimeMillis();
        PersistenceManager pm = Persistence.getPersistenceManager();
        try {
            Query theQuery = pm.newQuery(CondensedMatch.class);
            AbstractQueryResult theResult = (AbstractQueryResult)theQuery.execute();
            Iterator<?> sqr = theResult.iterator();
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

                        boolean matchHadErrors = false;
                        if (theJSON.has("errors")) {
                            JSONArray theErrors = theJSON.getJSONArray("errors");
                            for (int j = 0; j < theErrors.length(); j++) {
                                for (int i = 0; i < theErrors.getJSONArray(j).length(); i++) {
                                    if (!theErrors.getJSONArray(j).getString(i).isEmpty()) {
                                        matchHadErrors = true;
                                    }
                                }
                            }
                        }

                        if (!matchHadErrors && c.getPlayers().size() >= 2) {
                            theKeySet.add(new SortableTinyMatch(c.getPlayers(), theJSON));
                        }

                        // Score-related statistics.                        
                        for (int i = 0; i < c.getPlayers().size(); i++) {
                            String aPlayer = c.getPlayers().get(i);
                            int aPlayerScore = theJSON.getJSONArray("goalValues").getInt(i);

                            if (!matchHadErrors) {
                                if (!netScores.containsKey(aPlayer)) {
                                    netScores.put(aPlayer, 0.0);
                                }
                                netScores.put(aPlayer, netScores.get(aPlayer) + (aPlayerScore-50.0)/50.0);
                            }

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

                            if (!averageScoreOn.containsKey(aPlayer)) {
                                averageScoreOn.put(aPlayer, new HashMap<String,WeightedAverage>());
                            }
                            if (!averageScoreOn.get(aPlayer).containsKey(theGame)) {
                                averageScoreOn.get(aPlayer).put(theGame, new WeightedAverage());
                            }
                            averageScoreOn.get(aPlayer).get(theGame).addValue(aPlayerScore);

                            for (int j = 0; j < c.getPlayers().size(); j++) {
                                String bPlayer = c.getPlayers().get(j);
                                int bPlayerScore = theJSON.getJSONArray("goalValues").getInt(j);
                                if (bPlayer.equals(aPlayer))
                                    continue;

                                if (!averageScoreVersus.containsKey(aPlayer)) {
                                    averageScoreVersus.put(aPlayer, new HashMap<String,WeightedAverage>());
                                }
                                if (!averageScoreVersus.get(aPlayer).containsKey(bPlayer)) {
                                    averageScoreVersus.get(aPlayer).put(bPlayer, new WeightedAverage());
                                }
                                averageScoreVersus.get(aPlayer).get(bPlayer).addValue(aPlayerScore);
                                
                                if (!playerWinsVersusPlayerOnGame.containsKey(aPlayer)) {
                                    playerWinsVersusPlayerOnGame.put(aPlayer, new HashMap<String,Map<String,WinLossCounter>>());
                                }
                                if (!playerWinsVersusPlayerOnGame.get(aPlayer).containsKey(bPlayer)) {
                                    playerWinsVersusPlayerOnGame.get(aPlayer).put(bPlayer, new HashMap<String,WinLossCounter>());
                                }
                                if (!playerWinsVersusPlayerOnGame.get(aPlayer).get(bPlayer).containsKey(theGame)) {
                                    playerWinsVersusPlayerOnGame.get(aPlayer).get(bPlayer).put(theGame, new WinLossCounter());
                                }
                                playerWinsVersusPlayerOnGame.get(aPlayer).get(bPlayer).get(theGame).addEntry(aPlayerScore, bPlayerScore);
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
                
                theJSON = null;
                c = null;
            }
        } catch(JDOObjectNotFoundException e) {
            throw new RuntimeException(e);
        } finally {
            pm.close();
        }
        
        /* Compute more statistics in a second pass over matches, now sorted by time */
        EloRank theEloRank = new EloRank();
        for (SortableTinyMatch k : theKeySet) {
            k.getGoalValues();
            k.getPlayers();

            for (int i = 0; i < k.getPlayers().length; i++) {
                String aPlayer = k.getPlayers()[i];
                int aPlayerScore = k.getGoalValues()[i];
                for (int j = i+1; j < k.getPlayers().length; j++) {
                    String bPlayer = k.getPlayers()[j];
                    int bPlayerScore = k.getGoalValues()[j];
                    theEloRank.addNextMatch(aPlayer, bPlayer, aPlayerScore, bPlayerScore);
                }
            }
        }
        
        /* Finally we're done with computing statistics */
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
            overall.put("computeRAM", Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());
            overall.put("leaderboard", playerAverageScore);
            overall.put("decayedLeaderboard", playerDecayedAverageScore);
            overall.put("computedAt", System.currentTimeMillis());
            overall.put("matchesInPastHour", nMatchesInPastHour);
            overall.put("matchesInPastDay", nMatchesInPastDay);
            overall.put("matchesPerDayMedian", matchesPerDay.getMedianPerDay());
            overall.put("eloRank", theEloRank.getComputedRanks());
            overall.put("netScore", netScores);
            overall.put("statsVersion", STATS_VERSION);

            // Store the per-player statistics
            for (Player p : Player.loadPlayers()) {
                String playerName = p.getName();
                perPlayer.put(playerName, new JSONObject());

                perPlayer.get(playerName).put("averageScore", playerAverageScore.get(playerName));                
                perPlayer.get(playerName).put("decayedAverageScore", playerDecayedAverageScore.get(playerName));
                perPlayer.get(playerName).put("averageScoreOn", averageScoreOn.get(playerName));
                perPlayer.get(playerName).put("averageScoreVersus", averageScoreVersus.get(playerName));
                perPlayer.get(playerName).put("medianErrorsPerDay", playerErrorsPerDay.get(playerName));
                perPlayer.get(playerName).put("winsVersusPlayerOnGame", playerWinsVersusPlayerOnGame.get(playerName));                
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