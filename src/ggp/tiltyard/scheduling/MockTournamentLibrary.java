package ggp.tiltyard.scheduling;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

//TODO: Get rid of this entire file once ggp-tournament is integrated.


//Fake class until we can incorporate ggp-tournament 
interface Tournament {
 String getInternalName();
 String getDisplayName();
 //Optional<ZonedDateTime> getInitialStartTime();
 long getSecondsToWaitUntilInitialStartTime();
 
 NextMatchesResult getMatchesToRun(Seeding initialSeeding, Set<MatchResult> resultsSoFar);
}

//Fake class until we can incorporate ggp-tournament
class TournamentSpecParser {
	static class MockTournament implements Tournament {
		public String getInternalName() { return "test_tourney_2"; }
		public String getDisplayName() { return "Test Tourney 2"; }
		public long getSecondsToWaitUntilInitialStartTime() { return Math.max(0, BEGIN_TIME - System.currentTimeMillis()); }
		@Override
		public NextMatchesResult getMatchesToRun(Seeding initialSeeding, Set<MatchResult> resultsSoFar) {
			NextMatchesResult nextToRun = new NextMatchesResult();
			
			Set<String> doneInternalIDs = new HashSet<String>();
			for (MatchResult result : resultsSoFar) {
				if (!result.wasAborted()) {
					doneInternalIDs.add(result.getMatchId());
				}
			}

			if (!doneInternalIDs.contains("opaque-id-1")) {
				nextToRun.addMatchSetup(MatchSetup.create("opaque-id-1", TGame.create("http://games.ggp.org/base/games/ticTacToe/v0/", 2, true), Arrays.asList(new String[]{ "LabOne", "LabTwo" }), 30, 10));
			} else if (!doneInternalIDs.contains("opaque-id-2")) {
				nextToRun.addMatchSetup(MatchSetup.create("opaque-id-2", TGame.create("http://games.ggp.org/base/games/ticTacToe/v0/", 2, true), Arrays.asList(new String[]{ "LabTwo", "LabOne" }), 30, 10));
			} else if (!doneInternalIDs.contains("opaque-id-3") || !doneInternalIDs.contains("opaque-id-4")) {
				if (!doneInternalIDs.contains("opaque-id-3")) {
					nextToRun.addMatchSetup(MatchSetup.create("opaque-id-3", TGame.create("http://games.ggp.org/base/games/maze/v0/", 1, true), Arrays.asList(new String[]{ "LabOne" }), 30, 10));
				}
				if (!doneInternalIDs.contains("opaque-id-4")) {
					nextToRun.addMatchSetup(MatchSetup.create("opaque-id-4", TGame.create("http://games.ggp.org/base/games/maze/v0/", 1, true), Arrays.asList(new String[]{ "LabTwo" }), 30, 10));
				}
			} else if (!doneInternalIDs.contains("opaque-id-5")) {
				nextToRun.addMatchSetup(MatchSetup.create("opaque-id-5", TGame.create("http://games.ggp.org/base/games/connectFour/v0/", 2, true), Arrays.asList(new String[]{ "LabTwo", "LabOne" }), 30, 10));
			} else if (!doneInternalIDs.contains("opaque-id-6")) {
				nextToRun.addMatchSetup(MatchSetup.create("opaque-id-6", TGame.create("http://games.ggp.org/base/games/connectFour/v0/", 2, true), Arrays.asList(new String[]{ "LabOne", "LabTwo" }), 30, 10));
			}
			return nextToRun;
		}		
	}
	
	public static Tournament parseYamlString(String yamlString) {
		return new MockTournament();
	}
	
	final static long FIVE_MINUTES = 1000*60*5;
	final static long ONE_HOUR = 1000*60*60;
	final static long ONE_DAY = ONE_HOUR*24;
	
	final static long PLACEMARK = 1447637403146L; // 5:30PM on 11/15 in milliseconds
	final static long BEGIN_TIME = PLACEMARK + 3*ONE_HOUR + 10*FIVE_MINUTES; // 8:30PM on 11/15 in milliseconds
}

class Seeding {
    public String toPersistedString() {
    	return "yodawg";
    }

    public static Seeding fromPersistedString(String persistedString) {
    	return new Seeding();
    }
}

class TGame {
    private final String url;
    private final int numRoles;
    private final boolean fixedSum;

    private TGame(String url, int numRoles, boolean fixedSum) {
    	this.url = url;
        this.numRoles = numRoles;
        this.fixedSum = fixedSum;
    }

    public static TGame create(String url, int numRoles, boolean fixedSum) {
        return new TGame(url, numRoles, fixedSum);
    }

    public String getURL() {
        return url;
    }

    public int getNumRoles() {
        return numRoles;
    }

    public boolean isFixedSum() {
        return fixedSum;
    }
}

class MatchSetup {
    private final String matchId;
    private final TGame game;
    private final ImmutableList<String> players;
    private final int startClock;
    private final int playClock;

    private MatchSetup(String matchId, TGame game, ImmutableList<String> players, int startClock,
            int playClock) {
        this.matchId = matchId;
        this.game = game;
        this.players = players;
        this.startClock = startClock;
        this.playClock = playClock;
    }

    public static MatchSetup create(String matchId, TGame game, List<String> players, int startClock,
            int playClock) {
        return new MatchSetup(matchId, game, ImmutableList.copyOf(players),
                startClock, playClock);
    }

    public String getMatchId() {
        return matchId;
    }

    public TGame getGame() {
        return game;
    }

    public List<String> getPlayers() {
        return players;
    }

    public int getStartClock() {
        return startClock;
    }

    public int getPlayClock() {
        return playClock;
    }

    public String toString() {
        return "MatchSetup [matchId=" + matchId + ", game=" + game + ", players=" + players + ", startClock="
                + startClock + ", playClock=" + playClock + "]";
    }
}

class NextMatchesResult {
	private Set<MatchSetup> theMatches;
	public NextMatchesResult() {
		theMatches = new HashSet<MatchSetup>();
	}
	public void addMatchSetup(MatchSetup m) {
		theMatches.add(m);
	}
	ImmutableSet<MatchSetup> getMatchesToRun() {
		return new ImmutableSet.Builder<MatchSetup>().addAll(theMatches).build();
	};
	long getSecondsToWaitUntilAllowedStartTime() {
		return Math.max(0, TournamentSpecParser.BEGIN_TIME - System.currentTimeMillis());
	}
	public String toString() {
		StringBuilder sb = new StringBuilder("{ ");
		for(MatchSetup x : theMatches) {
			sb.append("< " + x.toString() + " >, ");
		}
		sb.append(" }");
		return sb.toString();
	}
}

class MatchResult {
    private final String matchId;
    private final ImmutableList<String> players;
    private final Outcome outcome;
    private final ImmutableList<Integer> goals;

    private MatchResult(String matchId, ImmutableList<String> players,
            Outcome outcome, ImmutableList<Integer> goals) {
        this.matchId = matchId;
        this.players = players;
        this.outcome = outcome;
        this.goals = goals;
    }

    public static MatchResult getAbortedMatchResult(String matchId, List<String> players) {
        return new MatchResult(matchId, ImmutableList.copyOf(players),
                Outcome.ABORTED, null);
    }

    public static MatchResult getSuccessfulMatchResult(String matchId, List<String> players, List<Integer> goals) {
        return new MatchResult(matchId, ImmutableList.copyOf(players),
                Outcome.COMPLETED, ImmutableList.copyOf(goals));
    }

    public String getMatchId() {
        return matchId;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public List<Integer> getGoals() {
        return goals;
    }

    public ImmutableList<String> getPlayers() {
        return players;
    }

    public boolean wasAborted() {
        return outcome == Outcome.ABORTED;
    }

    public static enum Outcome {
        COMPLETED,
        ABORTED
    }
    
    public String toString() {
        return "MatchResult [matchId=" + matchId + ", players=" + players + ", outcome=" + outcome + ", goals=" + goals
                + "]";
    }    
}