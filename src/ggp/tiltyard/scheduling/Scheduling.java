package ggp.tiltyard.scheduling;

import external.JSON.JSONArray;
import external.JSON.JSONException;
import external.JSON.JSONObject;
import ggp.tiltyard.players.Player;
import ggp.tiltyard.backends.Backends;
import ggp.tiltyard.hosting.Hosting;
import ggp.tiltyard.hosting.MatchData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletResponse;

import net.alloyggp.tournament.api.TMatchSetup;
import net.alloyggp.tournament.api.TNextMatchesResult;
import net.alloyggp.tournament.api.TPlayer;
import net.alloyggp.tournament.api.TSeeding;

import org.ggp.base.util.gdl.factory.exceptions.GdlFormatException;
import org.ggp.base.util.loader.RemoteResourceLoader;
import org.ggp.base.util.symbol.factory.exceptions.SymbolFormatException;

import com.google.common.collect.Lists;

public class Scheduling {
    // Eventually we should support other repository servers. Figure out how
    // to do this in a safe, secure fashion (since the repository server can
    // inject arbitrary javascript into the visualizations).	
	private static final String GAME_REPO_URL = "http://games.ggp.org/base/";
	
	// This is the tournament name for all of the regularly-scheduled Tiltyard
	// matches, the ones not initiated by manual action or as part of a special
	// "Tiltyard Open" tournament.
	private static final String REGULAR_TOURNAMENT_NAME = "tiltyard_continuous";

	// When running a special tournament, how many seconds before the tournament
	// should opted-in players be excluded from scheduling?
	private static final long SECONDS_BEFORE_TOURNEY_TO_STOP_NEW_MATCHES = 12 * 60 * 60; // 12 hours
	
	// This is a whitelist of games that run on Tiltyard, organized by category.
	// This eliminates the following classes of games:
	//
	// * Large complex games (e.g. Amazons, Knight Fight)
	// * Game rulesheets with known bugs (e.g. Chess)
	// * Games without base/input propositions, like:
	//
	// "beatMania", "bomberman2p", "breakthroughHoles", "breakthroughSuicide",
	// "chickentictactoe", "dualConnect4", "ghostMaze2p", "god", "golden_rectangle",
	// "lightsOut", "numberTicTacToe", "pacman2p", "pacman3p", "pawnToQueen", "pawnWhopping",
	// "snake2p", "snakeParallel", "tictactoe_3player", "ticTacToeParallel", "ticTacToeSerial", "ticTicToe",
	//
	// Ideally we'll be able to get rid of this whitelist at some point and
	// just use all of the games in the base repository.
	private static final String[][] safeGamesByCategory = {
		// Game theory games
		new String[] { "gt_attrition", "gt_centipede",
				"gt_chicken", "gt_dollar", "gt_prisoner", "gt_ultimatum", "gt_staghunt",
				"gt_coordination", "gt_tinfoil", "gt_two_thirds_2p", "gt_two_thirds_4p",
				"gt_two_thirds_6p" },
		// Chinese checkers variants
		new String[] { "chineseCheckers1", "chineseCheckers2", "chineseCheckers3",
				"chineseCheckers4", "chineseCheckers6" },
		// Sudoku variants
		new String[] { "sudokuGrade1", "sudokuGrade2", "sudokuGrade3", "sudokuGrade4",
				"sudokuGrade5", "sudokuGrade6E", "sudokuGrade6H" },
		// Futoshiki variants
		new String[] { "futoshiki4", "futoshiki5", "futoshiki6" },
		// FFA/TTC variants
		new String[] { "2pffa_zerosum", "2pffa", "3pffa", "4pffa",
				"2pttc", "3pttc", "4pttc", "ttcc4_2player", },
		// Checkers variants
		new String[] { "englishDraughts", "checkersSmall", "checkersTiny", "checkers",
				"chinook" },
		// Connect Four variants
		new String[] { "3pConnectFour", "connectFourLarger", "connectFourLarge",
				"connectFour", "connectFourSuicide", "connectFourSimultaneous" },
		// Tic-Tac-Toe variants
		new String[] { "ticTacToe", "nineBoardTicTacToe", "cittaceot", "ticTacToeLarge",
				"connect5", "biddingTicTacToe", "ticTacToeLargeSuicide",
				"biddingTicTacToe_10coins", "nineBoardTicTacToePie" },
		// Breakthrough variants
		new String[] { "knightThrough", "breakthroughWalls", "breakthrough",  "breakthroughSmall",
				"escortLatch" },
		// Dots-and-Boxes variants
		new String[] { "dotsAndBoxes", "dotsAndBoxesSuicide" },				
		// Pentago variants
		new String[] { "pentago", "pentagoSuicide" },
		// Quarto variants
		new String[] { "quarto", "quartoSuicide" },
		// Knight's Tour variants
		new String[] { "knightsTour", "knightsTourLarge" },
		// Chess variantsanObject
		new String[] { "speedChess", "skirmishNew", "skirmishZeroSum" },
		// Peg Jumping variants
		new String[] { "peg", "pegEuro" },
		// Hex variants
		new String[] { "hex", "hexPie", "majorities" },
		// Amazons variants
		new String[] { "amazons_8x8", "amazons_10x10", "amazonsSuicide_10x10", "amazonsTorus_10x10" },
		// Queens variants
		new String[] { "queens06ug", "queens08lg", "queens08ug" },
		// Games that fell into no other category, but didn't seem to be
		// significant enough to deserve their own individual categories.
		new String[] { "cephalopodMicro", "reversi", "maze", "eightPuzzle",
				"qyshinsu", "blocker", "sheepAndWolf", "max_knights", "untwistycomplex2",
				"tron_10x10", "mineClearingSmall" },
		// New games, that get an extra promotional boost because they're new or interesting
		new String[] { "futoshiki4", "futoshiki5", "futoshiki6", "queens06ug",
				"queens08lg", "queens08ug", "queens31lg", "mineClearingSmall" },
	};
	
    public static void runSchedulingRound() throws IOException {
    	SchedulerConfig theConfig = SchedulerConfig.loadConfig();
        List<Player> theAvailablePlayers = Player.loadEnabledPlayers();
        long morePlayersIn = Long.MAX_VALUE;
        
        Map<String, Set<MatchData>> activeTournamentMatches = new HashMap<String, Set<MatchData>>();
        
        {
            // Find and clear all of the completed or wedged matches. For matches
            // which are still ongoing, mark the players in those matches as busy.
        	// Also keep track of how long it will be before some are available.
        	Set<MatchData> activeMatches = MatchData.loadMatches();
            Set<String> busyPlayerNames = new HashSet<String>();            
            for (MatchData activeMatch : activeMatches) {
        		// Track all active matches by which tournament they're in.
        		String tournamentName = activeMatch.getTournamentId();
        		if (!activeTournamentMatches.containsKey(tournamentName)) {
        			activeTournamentMatches.put(tournamentName, new HashSet<MatchData>());
        		}
        		activeTournamentMatches.get(tournamentName).add(activeMatch);
        		
        		// Delete matches that just finished. Track which players are busy.
            	List<String> matchPlayers = activeMatch.getPlayerNames();
            	if (activeMatch.isCompleted() && activeMatch.getTimeSinceLastChange() > 1000L*60*2) {
            		// Once a match has been completed for 2+ minutes, force it to be published one
            		// last time, handle its consequences for player scheduling, and then delete it.
            		handleStrikesForPlayers(activeMatch.getMatchInfo(), matchPlayers, theAvailablePlayers);
            		activeMatch.publish();
            		activeMatch.delete();
            	} else if (activeMatch.isWedged()) {
            		// When a match becomes wedged for whatever reason, issue abort requests to all of
            		// the involved players, force it to be published one last time, and then delete it.
            		activeMatch.abort();
            		activeMatch.publish();
            		activeMatch.delete();
            	} else if (activeMatch.hasComputerPlayers()) {
            		// For active matches with computer players, mark those players as busy and
            		// note when they'll be available for new matches.
            		busyPlayerNames.addAll(matchPlayers);
            		morePlayersIn = Math.min(activeMatch.getExpectedTimeToCompletion(), morePlayersIn);
            	}
            }

            // For all of the players listed as enabled, record whether or not they're actually
            // identifying themselves as available. For players that are marked as pingable, we
            // only assign them matches if they don't identify themselves as busy.
            for (int i = theAvailablePlayers.size()-1; i >= 0; i--) {
                Player p = theAvailablePlayers.get(i);
                if (p.isEnabled() && p.isPingable()) {
                	p.doPing();
                }
            }

            // Remove all of the players that aren't actually available, either because
            // we know they're in a match, or because they say they're busy, or because
            // they're disabled.
            for (int i = theAvailablePlayers.size()-1; i >= 0; i--) {
                Player p = theAvailablePlayers.get(i);                
                if (!p.isEnabled() || (p.isPingable() && !("available".equals(p.getInfoStatus().toLowerCase()))) || busyPlayerNames.contains(p.getName())) {
                    theAvailablePlayers.remove(i);
                }
            }
        }

        // At this point we've gotten everything up to date, and the only thing
        // left to do is schedule new matches. If the backends are being drained,
        // we don't schedule any new matches.
        if (theConfig.isDrained()) return;
        
        // If there are no backends available, don't schedule any new matches.
        if (Backends.loadBackends().getFarmBackendAddresses(Player.REGION_ANY).isEmpty()) return;
        
        // When a special tournament is running or approaching, first run the special tournament
        // scheduling logic. This drains matches from players that will be involved in upcoming
        // and ongoing tournaments, and then once a tournament begins, schedules matches for that
        // tournament according to a particular lineup. Players not opted-in to the tournament do
        // not have their scheduling affected.        
        for (TournamentData tournament : TournamentData.loadTournaments()) {
        	long secondsToStart = tournament.getTournament().getSecondsToWaitUntilInitialStartTime();
        	
        	// Tournaments that have finished don't have any effect on scheduling.
        	if (tournament.hasFinished()) {
        		continue;
        	}

        	// For ongoing tournaments, special tournament scheduling rules are in effect.
        	if (secondsToStart == 0L) {
        		// First, check if the tournament has been seeded yet. If not, do so now.
        		if (!tournament.hasBegun()) {
	        		// The time has arrived. Begin the tournament!
        			runTournamentSetup(tournament, theAvailablePlayers);
        		}
        		
        		// Second, if the tournament has been seeded, schedule matches for it.
        		// It's possible for "runTournamentSetup" to not seed the tournament if
        		// there are no available players; in that case, the scheduling does not
        		// begin yet, and we wait for available players to show up before starting.
        		if (tournament.hasBegun()) {
	        		// At this point, the tournament has been seeded. Now we need to schedule
	        		// any matches that should be ongoing at this point but aren't yet active.
	        		try {
	        			runTournamentScheduler(tournament, activeTournamentMatches.get(tournament.getTournamentKey()));
	        		} catch (Exception e) {
	        			Logger.getAnonymousLogger().log(Level.SEVERE, "Could not schedule for tournament " + tournament.getTournamentKey() + ": " + e, e);
	        		}
	        		
	        		// Lastly, drain ordinary scheduling for all players involved in the tournament.
	                for (int i = theAvailablePlayers.size()-1; i >= 0; i--) {
	                    Player p = theAvailablePlayers.get(i);
	                    if (tournament.getPlayersInvolved().contains(p.getName())) {
	                        theAvailablePlayers.remove(i);
	                    }
	                }
        		}
        		
        		// For active tournaments, update the display data cache on every round.
        		tournament.updateDisplayDataCache();
            }
        	
        	// For upcoming tournaments, drain regular scheduling for all potentially-involved players.
        	if (!tournament.hasBegun() && secondsToStart < SECONDS_BEFORE_TOURNEY_TO_STOP_NEW_MATCHES) {
                for (int i = theAvailablePlayers.size()-1; i >= 0; i--) {
                    Player p = theAvailablePlayers.get(i);
                    if (p.isRegisteredForTourney()) {
                        theAvailablePlayers.remove(i);
                    }
                }
            }
        }
        
        // Figure out how many players are available. If no computer players are
    	// available, don't bother attempting to automatically schedule a match.
        if (theAvailablePlayers.isEmpty()) return;
        
        // For all of the pending matches in the scheduling queue, consider
        // running each one. These have a higher priority than the regularly
        // scheduled automatic matches. When they schedule, they will remove
        // the appropriate players from the list of available players.
        Set<PendingMatch> pendingMatches = PendingMatch.loadPendingMatches();
    	for (PendingMatch match : pendingMatches) {
    		match.considerStarting(theAvailablePlayers);
    	}        

        // When there is only a single available player, but there are busy players,
        // and we expect one of those busy players to become available within 15 minutes,
        // don't assign that player into a match vs all random opponents. Instead, wait
        // until a real opponent becomes available.
        if (theAvailablePlayers.size() == 1 && morePlayersIn < 15*60*1000) {
        	return;
        }
        
        // Load the aggregated game metadata from the base repository server.
        int nGamesLookupAttempt = 0;
        JSONObject metadataForGames;
        while (true) {        	
	        try {
	        	metadataForGames = RemoteResourceLoader.loadJSON(GAME_REPO_URL + "games/metadata");
	            break;
	        } catch (Exception e) {
	        	if (nGamesLookupAttempt > 9) {
	        		throw new RuntimeException(e);
	        	}
	        }
	        nGamesLookupAttempt++;
	        // Backoff between requests
        	try {
				Thread.sleep(1000);
			} catch (InterruptedException e1) {
				;
			}	        
        }        
        
        // Only allow games on a whitelist of "safe" games that the backend can handle.
        // Set<String> theSafeGames = new HashSet<String>(Arrays.asList(safeGames));
        Set<String> theSafeGames = new HashSet<String>(Arrays.asList(safeGamesByCategory[new Random().nextInt(safeGamesByCategory.length)]));
        List<String> properGameKeys = new ArrayList<String>();
        Map<String, JSONObject> properGames = new HashMap<String, JSONObject>();        
        Iterator<?> itr = metadataForGames.keys();
        while (itr.hasNext()) {
        	String key = itr.next().toString();
        	if (!theSafeGames.contains(key))
        		continue;
        	try {
	        	JSONObject gameMetadata = metadataForGames.getJSONObject(key);
        		properGames.put(key, gameMetadata);
        		properGameKeys.add(key);
        	} catch (JSONException e) {
        		throw new RuntimeException(e);
        	}
        }

        // Shuffle the list of known proper games, draw a game, and get ready to play.
        Collections.shuffle(properGameKeys);
        String gameKey = properGameKeys.get(0);        
        int nPlayersForGame, gameVersion;
        try {
	        nPlayersForGame = properGames.get(gameKey).getInt("numRoles");
	        gameVersion = properGames.get(gameKey).getInt("version");
        } catch (JSONException e) {
        	throw new RuntimeException(e);
        }
        String theGameURL = GAME_REPO_URL + "games/" + gameKey + "/v" + gameVersion + "/";

        // Shuffle the set of available players and then assign them to roles
        // in the game until we run out of players or roles. If we run out of
        // players, assign random players to fill the remaining roles. Lastly
        // shuffle the mapping of players to roles so that the random players
        // aren't always playing the last roles in the game.
        List<Player> theChosenPlayers = new ArrayList<Player>();
        Collections.shuffle(theAvailablePlayers);
        for (Player p : theAvailablePlayers) {
        	nPlayersForGame--;
        	theChosenPlayers.add(p);
        	if (nPlayersForGame == 0)
        		break;
        }
        while (nPlayersForGame > 0) {
        	nPlayersForGame--;
        	// null stands in for a random player.
        	theChosenPlayers.add(null);
        }
        Collections.shuffle(theChosenPlayers);
        
        // Once the final mapping of players to roles has been chosen,
        // extract the names and URLs from the players into a form that
        // will appear in the match creation request.
        List<String> playerURLsForMatch = new ArrayList<String>();
        List<String> playerNamesForMatch = new ArrayList<String>();
        List<String> playerRegionsForMatch = new ArrayList<String>();
        for (Player p : theChosenPlayers) {        	
        	if (p == null) {
        		playerURLsForMatch.add(null);
        		playerNamesForMatch.add("Random");
        		playerRegionsForMatch.add(Player.REGION_ANY);
        	} else {
        		playerURLsForMatch.add(p.getURL());
        		playerNamesForMatch.add(p.getName());
        		playerRegionsForMatch.add(p.getRegion());
        	}
        }
        
        // Choose randomized start clocks and play clocks for the match.
        // Start clocks vary between 150 seconds and 240 seconds.
        // Play clocks vary between 15 seconds and 45 seconds.
        Random theRandom = new Random();        
        int startClock = 150 + 10*theRandom.nextInt(10);
        int playClock = 15 + 5*theRandom.nextInt(7);
        int previewClock = -1;

        // Start the match using the hybrid match hosting system.
       	Hosting.startMatch(theGameURL, playerURLsForMatch, playerNamesForMatch, playerRegionsForMatch, previewClock, startClock, playClock, REGULAR_TOURNAMENT_NAME);
    }
    
    private static void runTournamentSetup(TournamentData tournament, List<Player> theAvailablePlayers) {
    	// First, choose which players are participating in this tournament.
		Set<String> playersForTourney = new HashSet<String>();
        for (int i = theAvailablePlayers.size()-1; i >= 0; i--) {
            Player p = theAvailablePlayers.get(i);
            if (p.isRegisteredForTourney()) {
            	playersForTourney.add(p.getName());
            }
        }
        if (!playersForTourney.isEmpty()) {
	        // Then, create a seeding that includes those players.
	        List<TPlayer> players = Lists.newArrayList();
	        for (String player : playersForTourney) {
	        	players.add(TPlayer.create(player));
	        }
	        TSeeding seeding = TSeeding.createRandomSeeding(new Random(), players);
	        String persistedSeeding = seeding.toPersistedString();
	        tournament.beginTournament(playersForTourney, persistedSeeding);
        }
    }
    
    private static void runTournamentScheduler(TournamentData tournament, Set<MatchData> activeTournamentMatches) throws JSONException, IOException, SymbolFormatException, GdlFormatException {
		Set<String> activeInternalMatchIDs = new HashSet<String>();
		if (activeTournamentMatches != null) {
			for (MatchData activeMatch : activeTournamentMatches) {
				activeInternalMatchIDs.add(tournament.lookupInternalMatchID(activeMatch.getMatchId()));
			}
		}
		
        // Get the results for already-completed matches in this tournament.
		// Use those results to compute the set of next matches to run.
        TNextMatchesResult nextMatchesResult = tournament.getNextMatches();
        
        if (nextMatchesResult.getSecondsToWaitUntilAllowedStartTime() > 0L) {
            // This round of the tournament shouldn't start yet.
            // Let a later pass of the scheduler deal with these matches.
            return;
        }

        if (nextMatchesResult.getMatchesToRun().isEmpty()) {
            tournament.finishTournament();
            return;
        }

        for (TMatchSetup matchSetup : nextMatchesResult.getMatchesToRun()) {
            if (activeInternalMatchIDs.contains(matchSetup.getMatchId())) {
            	Logger.getAnonymousLogger().severe("Skipping duplicate scheduling for " + matchSetup.getMatchId() + " since it's active (from active ids).");
                continue;
            }
            
            if (tournament.hasInternalMatchID(matchSetup.getMatchId())) {
            	Logger.getAnonymousLogger().severe("Skipping duplicate scheduling for " + matchSetup.getMatchId() + " since it's active (from tournament).");
            	continue;
            }
            
            String theGameURL = matchSetup.getGame().getUrl();

            List<String> playerURLsForMatch = Lists.newArrayList();
            List<String> playerNamesForMatch = Lists.newArrayList();
            List<String> playerRegionsForMatch = Lists.newArrayList();
            for (TPlayer tournamentPlayer : matchSetup.getPlayers()) {
                Player player = Player.loadPlayer(tournamentPlayer.getId());
                playerURLsForMatch.add(player.getURL());
                playerNamesForMatch.add(player.getName());
                playerRegionsForMatch.add(player.getRegion());
            }
            String publicMatchID = Hosting.startMatch(theGameURL,
            		playerURLsForMatch,
                    playerNamesForMatch,
                    playerRegionsForMatch,
                    -1, //previewClock
                    matchSetup.getStartClock(),
                    matchSetup.getPlayClock(),
                    tournament.getTournamentKey());
            tournament.recordMatch(publicMatchID, matchSetup.getMatchId());
        }
    }

    private static void handleStrikesForPlayers(JSONObject theMatchInfo, List<String> players, List<Player> thePlayers) {    	
        if (!theMatchInfo.has("errors")) return;        
        int[] numLegalMoves = new int[players.size()];
        for (int i = 0; i < numLegalMoves.length; i++) {
        	numLegalMoves[i] = 0;
        }
        
        int numTotalMoves;
        try {
            JSONArray theErrors = theMatchInfo.getJSONArray("errors");
            numTotalMoves = theErrors.length();
            for (int i = 0; i < theErrors.length(); i++) {
                JSONArray moveErrors = theErrors.getJSONArray(i);
                for (int j = 0; j < moveErrors.length(); j++) {
                    if (moveErrors.getString(j).isEmpty()) {
                    	numLegalMoves[j]++;
                    }
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        for (int i = 0; i < thePlayers.size(); i++) {
            int nPlayerIndex = players.indexOf(thePlayers.get(i).getName());
            if (nPlayerIndex > -1) {
            	if (numLegalMoves[nPlayerIndex] >= numTotalMoves/2) {
                    thePlayers.get(i).resetStrikes();
                } else {
                    thePlayers.get(i).addStrike();
                }
                thePlayers.get(i).save();
            }
        }
    }
    
	public static void doPost(String theURI, String in, HttpServletResponse resp) throws IOException {
		try {
			if (theURI.equals("start_match")) {
				JSONObject theRequest = new JSONObject(in);

				// Extract player codes from the request.
				List<String> playerCodes = new ArrayList<String>();
		        JSONArray thePlayerCodes = theRequest.getJSONArray("playerCodes");
		        for (int i = 0; i < thePlayerCodes.length(); i++) {
		        	String playerCode = thePlayerCodes.getString(i);
		        	playerCodes.add(playerCode);
		        }

		        // Extract match parameters from the request.
		        String gameURL = theRequest.getString("gameURL");
		        int previewClock = theRequest.getInt("previewClock");
		        int startClock = theRequest.getInt("startClock");
		        int playClock = theRequest.getInt("playClock");
		        int deadline = theRequest.getInt("deadline");
		        
		        // All games on Tiltyard must come from the base game repository.
				if (!gameURL.startsWith("http://games.ggp.org/base/games/")) {
		        	resp.setStatus(500);
		        	return;
		        }

		        // Create the match and enqueue it (and possibly start it).
			    PendingMatch pending = new PendingMatch(gameURL, playerCodes, previewClock, startClock, playClock, System.currentTimeMillis() + deadline);
			    String matchKey = pending.considerStarting(new ArrayList<Player>());
			    if (matchKey == null) {
			    	resp.getWriter().println("queued");
			    } else {
			    	resp.getWriter().println(matchKey);
			    }
			}

            resp.setHeader("Access-Control-Allow-Origin", "tiltyard.ggp.org");
            resp.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
            resp.setHeader("Access-Control-Allow-Headers", "*");
            resp.setHeader("Access-Control-Allow-Age", "86400");        
            resp.setContentType("text/plain");
            resp.setStatus(200);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	public static void doGet(String reqURI, HttpServletResponse resp) {
		if (reqURI.equals("pending")) {
    		try {			
				List<JSONObject> pendingMatchesJSON = new ArrayList<JSONObject>();
		        Set<PendingMatch> pendingMatches = PendingMatch.loadPendingMatches();
	        	for (PendingMatch match : pendingMatches) {
	        			pendingMatchesJSON.add(match.toJSON());
	        	}
	        	JSONObject response = new JSONObject();
	        	response.put("pending", pendingMatchesJSON);        	
	        	resp.getWriter().println(response);
	            resp.setHeader("Access-Control-Allow-Origin", "*");
	            resp.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
	            resp.setHeader("Access-Control-Allow-Headers", "*");
	            resp.setHeader("Access-Control-Allow-Age", "86400");        
	            resp.setContentType("text/plain");
	            resp.setStatus(200);
    		} catch (JSONException je) {
    			throw new RuntimeException(je);
    		} catch (IOException ie) {
    			throw new RuntimeException(ie);
    		}
		}
	}
}