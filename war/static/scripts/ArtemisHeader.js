function renderMatchEntries(theMatchEntries, theOngoingMatches, topCaption, playerToHighlight) {
    loadBellerophonMetadataForGames();
    
    var theHTML = '<center><table class="matchlist">';
    theHTML += '<tr bgcolor=#E0E0E0><th height=30px colspan=10>' + topCaption + '</th></tr>';
    for (var i = 0; i < theMatchEntries.length; i++) {
      theHTML += renderMatchEntry(theMatchEntries[i], theOngoingMatches, playerToHighlight, i%2);
    }
    theHTML += "</table></center>";
    return theHTML;
}

function renderMatchEntry(theMatchJSON, theOngoingMatches, playerToHighlight, showShadow) {
  getGameName = function (x) { return getGameInfo(x).bellerophonName; };
    
  if ("errors" in theMatchJSON) {
      var noErrorCandidates = true;
      var hasErrors = false;
      var allErrors = true;
      var hasErrorsForPlayer = [];
      var allErrorsForPlayer = [];
      var allErrorsForSomePlayer = false;
      for (var i = 0; i < theMatchJSON.gameRoleNames.length; i++) {
        hasErrorsForPlayer.push(false);
        allErrorsForPlayer.push(true);
      }
      if ("errors" in theMatchJSON) {
        for (var i = 0; i < theMatchJSON.errors.length; i++) {
          for (var j = 0; j < theMatchJSON.errors[i].length; j++) {
            if (theMatchJSON.errors[i][j] != "") {
              hasErrors = true;
              hasErrorsForPlayer[j] = true;
            } else {
              allErrorsForPlayer[j] = false;
              allErrors = false;
            }
            noErrorCandidates = false;
          }
        }
      }
      if (noErrorCandidates) {
        // If there are no moves so far, technically "all moves have been errors",
        // but that's a confusing way to present things, so it's better to show the
        // equally-true information "all moves have been error-free".
        allErrors = false;
        for (var i = 0; i < allErrorsForPlayer.length; i++) {
          allErrorsForPlayer[i] = false;
        }
      }
      for (var i = 0; i < allErrorsForPlayer.length; i++) {
        if (allErrorsForPlayer[i]) {
          allErrorsForSomePlayer = true;
        }
      }
  } else {
    var hasErrors = theMatchJSON.hasErrors;
    var allErrors = theMatchJSON.allErrors;
    var hasErrorsForPlayer = theMatchJSON.hasErrorsForPlayer;
    var allErrorsForPlayer = theMatchJSON.allErrorsForPlayer;
    var allErrorsForSomePlayer = theMatchJSON.allErrorsForSomePlayer;
  }
  
  var theMatchHTML = "<tr>";
  if (showShadow == 1) {
    theMatchHTML = "<tr bgcolor=#E0E0E0>";
  } else {
    theMatchHTML = "<tr bgcolor=#F5F5F5>";
  }
  
  // Match start time.
  var theDate = new Date(theMatchJSON.startTime);
  theMatchHTML += '<td class="padded">';  
  if (theOngoingMatches.indexOf(theMatchJSON.matchURL) >= 0) {
	  theMatchHTML += '<b>';
  }
  theMatchHTML += UserInterface.renderDateTime(theDate);
  if (theOngoingMatches.indexOf(theMatchJSON.matchURL) >= 0) {
	  theMatchHTML += '</b>';
  }  
  theMatchHTML += "</td>"
  
  // Match players...
  theMatchHTML += '<td class="padded"><table class="matchlist" width=100%>';
  if ("matchRoles" in theMatchJSON) {
    var nPlayers = theMatchJSON.matchRoles;
  } else if ("playerNamesFromHost" in theMatchJSON) {
    var nPlayers = theMatchJSON.playerNamesFromHost.length;
  } else {
    var nPlayers = -1;
  }
  for (var j = 0; j < nPlayers; j++) {
    if ("playerNamesFromHost" in theMatchJSON && playerToHighlight == theMatchJSON.playerNamesFromHost[j]) {
        theMatchHTML += '<tr style="background-color: #CCEECC;">'
    } else {
        theMatchHTML += '<tr>'
    }
    if ("playerNamesFromHost" in theMatchJSON && theMatchJSON.playerNamesFromHost[j].length > 0) {
      theMatchHTML += '<td class="imageHolder" style="width:25px; padding-right:5px"><img width=25 height=25 src="http://placekitten.com/g/25/25"/></td>';
      theMatchHTML += '<td><a href="//www.ggp.org/view/tiltyard/players/' + theMatchJSON.playerNamesFromHost[j] + '">' + theMatchJSON.playerNamesFromHost[j] + '</a></td>';
    } else {
      theMatchHTML += '<td class="imageHolder" style="width:25px; padding-right:5px"><img width=25 height=25 src="//www.ggp.org/viewer/images/hosts/Unsigned.png" title="This player is not identified." /></td>';
      theMatchHTML += '<td>Anonymous</td>';
    }
    theMatchHTML += '<td width=5></td><td>';
    if (!("goalValues" in theMatchJSON) && "playerNamesFromHost" in theMatchJSON && theMatchJSON.playerNamesFromHost[j].length == 0 && "isPlayerHuman" in theMatchJSON && theMatchJSON.isPlayerHuman[j]) {
      theMatchHTML += '<a href="' + theMatchJSON.matchURL.replace("http://matches.ggp.org/matches/", "http://tiltyard.ggp.org/hosting/matches/") + 'player' + (j+1) + '/">(play)</a>';
    }
    theMatchHTML += '</td><td width=5></td>';
    theMatchHTML += '<td class="imageHolder">'
    if (allErrorsForPlayer[j]) {
      theMatchHTML += '<img width=20 height=20 src="//www.ggp.org/viewer/images/warnings/YellowAlert.png" title="This player had all errors in this match.">';
    } else if (hasErrorsForPlayer[j]) {
      theMatchHTML += '<img width=20 height=20 src="//www.ggp.org/viewer/images/warnings/WhiteAlert.png" title="This player had errors in this match.">';
    }
    theMatchHTML += '</td>'
    theMatchHTML += '<td width=5></td>';
    if ("goalValues" in theMatchJSON) {
      theMatchHTML += '<td class="padded" style="text-align: right;">' + theMatchJSON.goalValues[j] + '</td>';
    } else if ("isAborted" in theMatchJSON && theMatchJSON.isAborted) {
      theMatchHTML += '<td class="padded""><img src="//www.ggp.org/viewer/images/warnings/Abort.png" title="This match was aborted midway through." style="float:right;"></td>';
    } else {
      theMatchHTML += '<td class="padded"></td>';
    }
    theMatchHTML += '<td width=5></td></tr>';
  }
  theMatchHTML += '</table></td>';

  // Match game profile.
  theMatchHTML += '<td class="padded"><a href="//www.ggp.org/view/tiltyard/games/' + translateRepositoryIntoCodename(theMatchJSON.gameMetaURL) + '">' + UserInterface.trimTo(getGameName(theMatchJSON.gameMetaURL),20) + '</a></td>';
  theMatchHTML += '<td width=5></td>';
  
  // Signature badge.
  if ("hashedMatchHostPK" in theMatchJSON) {
    theMatchHTML += '<td class="imageHolder"><a href="//www.ggp.org/view/tiltyard/matches/"><img width=25 height=25 src="//www.ggp.org/viewer/images/hosts/Tiltyard.png" title="Match has a valid digital signature from Tiltyard."></img></a></td>';
  } else {
    theMatchHTML += '<td class="imageHolder"><img width=25 height=25 src="//www.ggp.org/viewer/images/hosts/Unsigned.png" title="Match does not have a valid digital signature."></img></td>';
  }
  theMatchHTML += '<td width=5></td>';
  
  // Match page URL.
  var matchURL = theMatchJSON.matchURL.replace("http://matches.ggp.org/matches/", "");
  theMatchHTML += '<td class="padded"><a href="//www.ggp.org/view/tiltyard/matches/' + matchURL + '">View</a></td>';
  theMatchHTML += '<td width=5></td>';
  return theMatchHTML + "</tr>";
}

global_gameMetadata = {};
function loadBellerophonMetadataForGames() {
  var theMetadata = ResourceLoader.load_json("//games.ggp.org/base/games/metadata");
  for (var gameKey in theMetadata) {
    global_gameMetadata["//games.ggp.org/base/games/" + gameKey + "/"] = theMetadata[gameKey];
  }

  getGameInfo = function (gameVersionedURL) {
    var splitURL = gameVersionedURL.split("/");
    var versionFromURL = 1*(splitURL[splitURL.length-2].replace("v",""));
    if (isNaN(versionFromURL)) {
      versionFromURL = null;
      // NOTE: Explore how often this happens?
      //alert('getGameInfo got a non-versioned URL!');
    } else {
      splitURL = splitURL.splice(0,splitURL.length-2);
      splitURL.push('');
    }
    var gameUnversionedURL = splitURL.join("/").replace("http:", "");
    // TODO: Ultimately we should look up version-specific metadata?
    var gameInfo = global_gameMetadata[gameUnversionedURL];

    gameInfo.bellerophonLink = '//www.ggp.org/view/tiltyard/games/' + translateRepositoryIntoCodename(gameVersionedURL);
    gameInfo.bellerophonVersionFromURL = versionFromURL;

    if (!("gameName" in gameInfo)) {
      gameInfo.bellerophonName = UserInteface.toTitle(translateRepositoryIntoCodename(gameVersionedURL).split("/").splice(1)[0]);
    } else {
      gameInfo.bellerophonName = gameInfo.gameName;
    }
    return gameInfo;
  }
}

function translateRepositoryCodename(x) {
  return x.replace("base/", "http://games.ggp.org/base/games/").replace("dresden/", "http://games.ggp.org/dresden/games/");
}
function translateRepositoryIntoCodename(x) {
    return x.replace("http://games.ggp.org/base/games/", "base/").replace("http://games.ggp.org/dresden/games/", "dresden/");
}