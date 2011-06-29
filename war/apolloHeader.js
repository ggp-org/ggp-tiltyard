var loginNascarHTML;
function generateHeader(theDiv) {
    var theHTML = "";
    theHTML += '<center>';
    theHTML += '<table style="width: 100%; border: 0; margin: 0; border-spacing: 0px 0px; bgcolor: rgb(160,160,160);">';
    theHTML += '  <tr class="navbarTop">';
    theHTML += '    <td width=2% align="left"></td>';
    theHTML += '    <td width=18% align="left" valign="bottom"><a class=apollogo href="/">Apollo</a><span class=apollogo2>beta</span></td>';
    theHTML += '    <td width=10% align="center" valign="bottom"><a class=biglink href="/about/">About</a></td>';
    theHTML += '    <td width=10% align="center" valign="bottom"><a class=biglink href="/games/">Games</a></td>';
    theHTML += '    <td width=10% align="center" valign="bottom"><a class=biglink href="/players/">Players</a></td>';    
    theHTML += '    <td width=10% align="center" valign="bottom"><a class=biglink href="/matches/">Matches</a></td>';
    theHTML += '    <td width=10% align="center" valign="bottom"><a class=biglink href="/stats/">Stats</a></td>';
    theHTML += '    <td width=30% align="right" valign="bottom"><div class="login" id="login_div"> </div></td>';
    theHTML += '  </tr>';
    theHTML += '  <tr id="navBuffer" class="navbarBottom">'; 
    theHTML += '    <td colspan=10 height=10px></td>';
    theHTML += '  </tr>';
    theHTML += '</table>';
    theHTML += '</center>';
    theDiv.innerHTML = theHTML;
    
    var loginState = ResourceLoader.load_json('/data/login');
    var loginHTML = "";
    if (loginState.loggedIn) {
      if (loginState.nickname.length > 0) {
        loginHTML += "<b>" + loginState.nickname + "</b>";
      } else {
        loginHTML += "<b>Signed in.</b> ";
      }
      loginHTML += " <a class=\"darklink\" style='text-decoration:none;' href=\"" + loginState.logoutURL.replace("/REPLACEME", window.location.pathname) + "\">(sign out)</a>.";
    } else {
      loginHTML += " <a class=\"darklink\" href=\"javascript: document.getElementById('login_div').innerHTML = loginNascarHTML;\">Sign in</a> using OpenID.";
      loginNascarHTML = "Sign in using OpenID via <br>";
      for (var i in loginState.preferredOrder) {
        var providerName = loginState.preferredOrder[i];
        loginNascarHTML += "<a rel=\"nofollow\" href=\"" + loginState.providers[providerName].replace("/REPLACEME", window.location.pathname) + "\"><img src=\"/static/images/" + providerName + ".png\"></img></a> ";
      }
    }      
    document.getElementById('login_div').innerHTML = loginHTML;
}

function generatePlayerHTML(aPlayer) {
    var thePlayerHTML = '<table class="player" id="player_' + aPlayer.name + '_table" style="background-color:';
    if ("theURL" in aPlayer) {
        thePlayerHTML += '#CCEECC; height: 110px';
    } else {
        thePlayerHTML += '#DDDDDD; height: 80px';
    }
    thePlayerHTML += '">';
    thePlayerHTML += generatePlayerInnerHTML(aPlayer);
    thePlayerHTML += '</table>';    
    return thePlayerHTML;
}

var theRecordedPlayers = {};
function generatePlayerInnerHTML(aPlayer) {
    theRecordedPlayers[aPlayer.name] = aPlayer;
    
    function clip(s, n) {
        if (s.length <= n) return s;
        return s.substring(0,n-3) + "...";
    }
    
    var statusColor = 'grey';
    if ("pingStatus" in aPlayer) {
      if (aPlayer.pingStatus == "available") {
        statusColor = 'green';
      } else if (aPlayer.pingStatus == "busy") {
        statusColor = 'yellow';
      } else {
        statusColor = 'red';
      }
    }
    
    var thePlayerHTML = "";
    thePlayerHTML += '<tr><td width=5></td>';
    thePlayerHTML += '<td width=60><a style="text-decoration:none; color: #222222;" href="/players/' + aPlayer.name + '"><table style="border-width: 2px; border-style: inset; border-color: ' + statusColor + ';" cellspacing=0 cellpadding=0><tr><td><img width=50 height=50 src="http://placekitten.com/g/50/50"/></tr></td></table></a></td>';
    thePlayerHTML += '<td width=5></td>';
    thePlayerHTML += '<td width=255><a style="text-decoration:none; color: #222222;" href="/players/' + aPlayer.name + '"><font size=6><b>' + clip(aPlayer.name,15) + '</b></font></a>';
    thePlayerHTML += '<div id=player_' + aPlayer.name + '_email>'; 
    if (aPlayer.visibleEmail.length > 0) {
        thePlayerHTML += '<tt>' + clip(aPlayer.visibleEmail, 30) + '</tt>';
    } else {
        thePlayerHTML += '<i>Email address not listed.</i>';
    }
    thePlayerHTML += '</div></td>';
    thePlayerHTML += '<td width=5></td>';
    thePlayerHTML += '<td width=90>';
    if (aPlayer.isEnabled) {
        thePlayerHTML += '<table class="active"><tr id="player_' + aPlayer.name + '_active"><td>Active!</td></tr></table>';
    } else {
        thePlayerHTML += '<table class="inactive"><tr id="player_' + aPlayer.name + '_active"><td>Inactive</td></tr></table>'; 
    }
    thePlayerHTML += '<br>';
    thePlayerHTML += '<table class="gdlVersion"><tr><td>' + aPlayer.gdlVersion + '</td></tr></table>';
    thePlayerHTML += '</td></tr>';
    if ("theURL" in aPlayer) {
        thePlayerHTML += '<tr><td width=5></td>';
        thePlayerHTML += '<td><b>URL:</b></td><td width=5></td>';
        thePlayerHTML += '<td><div id=player_' + aPlayer.name + '_url>';
        if (aPlayer.theURL.length > 0) {
          thePlayerHTML += '<tt>' + aPlayer.theURL + '</tt>';
        } else {
          thePlayerHTML += '<i>Player URL not listed.</i>';
        }
        thePlayerHTML += '</div></td><td width=5></td>';
        thePlayerHTML += '<td><div id=player_' + aPlayer.name + '_button><button onclick=\'clickedEditForPlayer("' + aPlayer.name + '")\' type="Button">Edit</button></div></td></tr>'; 
    }
    return thePlayerHTML;
}

function clickedEditForPlayer (playerName) {
    var aPlayer = theRecordedPlayers[playerName];
    var urlDiv = document.getElementById("player_" + playerName + "_url");
    var emailDiv = document.getElementById("player_" + playerName + "_email");
    var activeTd = document.getElementById("player_" + playerName + "_active");
    var buttonDiv = document.getElementById("player_" + playerName + "_button");    
    
    urlDiv.innerHTML = '<input type="text" size="22" id="player_' + playerName + '_url_field" value="' + aPlayer.theURL + '">';
    emailDiv.innerHTML = '<input type="text" size="22" id="player_' + playerName + '_email_field" value="' + aPlayer.visibleEmail + '">';
    if (aPlayer.isEnabled) {
      activeTd.innerHTML = '<select id="player_' + playerName + '_active_field"><option>Inactive</option><option selected>Active</option></select>';
    } else {
      activeTd.innerHTML = '<select id="player_' + playerName + '_active_field"><option selected>Inactive</option><option>Active</option></select>';
    }
    buttonDiv.innerHTML = '<button onclick=\'clickedEditDoneForPlayer("' + aPlayer.name + '")\' type="Button">Done!</button>';
}

function clickedEditDoneForPlayer (playerName) {
    var aPlayer = theRecordedPlayers[playerName];
    
    var newActive = (document.getElementById("player_" + playerName + "_active_field").selectedIndex == 1);
    var newURL = document.getElementById("player_" + playerName + "_url_field").value;
    var newEmail = document.getElementById("player_" + playerName + "_email_field").value;
    aPlayer.theURL = newURL;
    aPlayer.visibleEmail = newEmail;
    aPlayer.isEnabled = newActive;

    // TODO: Make this asynchronous?
    var bPlayer = JSON.parse(ResourceLoader.post_raw("/data/updatePlayer", JSON.stringify(aPlayer)));    

    var theTable = document.getElementById("player_" + playerName + "_table");
    theTable.innerHTML = generatePlayerInnerHTML(bPlayer);
}

function renderJSON(x) {
  var s = "";
  if (typeof(x) == "object" && !x[0]) {    
    s += "<table border=\"1px\">";
    for (y in x) {
      s += "<tr><td><b>" + y + "</b></td><td>" + renderJSON(x[y]) + "</td></tr>";
    }
    s += "</table>";    
  } else {
    s += x;
  }
  return s;
}

function renderMatchEntries(theMatchEntries, theOngoingMatches, topCaption, playerToHighlight) {
    var theGames = ResourceLoader.load_json('/data/games/');
    
    var theHTML = '<center><table class="matchlist">';
    theHTML += '<tr bgcolor=#E0E0E0><th height=30px colspan=7>' + topCaption + '</th></tr>';
    for (var i = 0; i < theMatchEntries.length; i++) {
      theHTML += renderMatchEntry(theMatchEntries[i], theOngoingMatches, playerToHighlight, theGames, i%2);
    }
    theHTML += "</table></center>";
    return theHTML;
}

function renderMatchEntry(theMatchJSON, theOngoingMatches, playerToHighlight, theGames, showShadow) {
  var theGame = theGames[theMatchJSON.gameMetaURL];
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

  // TODO(schreib): Factor this out into a general function.
  renderDuration = function(x) {
      var s = Math.round(x/1000);
      var sV = "" + (s % 60);
      
      var m = Math.floor(s/60);
      var mV = "" + (m % 60);
      
      var h = Math.floor(m/60);
      var hV = "" + h;
      
      if (m != 0) {
          while (sV.length < 2) sV = "0" + sV;
      }
      if (h != 0) {
          while (mV.length < 2) mV = "0" + mV;
          while (sV.length < 2) sV = "0" + sV;
      }

      hV += ":";
      mV += ":";
      
      if (h == 0) {
        if (m == 0) {
          mV = "";
          sV += "s";
        }
        hV = "";
      }

      return hV + mV + sV;
  }
  
  // TODO(schreib): Find the right place for this.
  updateLiveDuration = function (objName, startTime) {
    var theSpan = document.getElementById(objName);
    theSpan.innerHTML = renderDuration(new Date() - new Date(startTime));
    setTimeout("updateLiveDuration('" + objName + "'," + startTime + ")", 1000);
  }

  var theMatchHTML = "<tr>";
  if (showShadow == 1) {
    theMatchHTML = "<tr bgcolor=#E0E0E0>";
  } else {
    theMatchHTML = "<tr bgcolor=#F5F5F5>";
  }
  
  // Match game profile.
  theMatchHTML += '<td class="padded"><a href="/games/' + translateRepositoryIntoCodename(theGame.gameMetaURL) + '">' + theGame.metadata.gameName + '</a></td>';

  // Match start time.
  var theDate = new Date(theMatchJSON.startTime);
  theMatchHTML += '<td class="padded">' + UserInterface.renderDateTime(theDate);
  if (theOngoingMatches.indexOf(theMatchJSON.apolloSpectatorURL) >= 0) {
      theMatchHTML += '<br><center><b>(Ongoing! <span id="dlx_' + theMatchJSON.randomToken + '">' + renderDuration(new Date() - new Date(theMatchJSON.startTime)) + '</span>)</b></center>';
      setTimeout("updateLiveDuration('dlx_" + theMatchJSON.randomToken + "'," + theMatchJSON.startTime + ")", 1000);
  }
  theMatchHTML += "</td>"  
  
  // Match players...
  theMatchHTML += '<td class="padded"><table class="matchlist" width=100%>';
  for (var j = 0; j < theMatchJSON.apolloPlayers.length; j++) {
    theMatchHTML += '<tr>'
    var highlightAttribute = '';    
    if (playerToHighlight == theMatchJSON.apolloPlayers[j]) {
      highlightAttribute = 'style="background-color: #CCEECC;"';
    }
    theMatchHTML += '<td class="padded"><a ' + highlightAttribute + ' href="/players/' + theMatchJSON.apolloPlayers[j] + '">' + theMatchJSON.apolloPlayers[j] + '</a>';
    if (allErrorsForPlayer[j]) {
      theMatchHTML += ' <img src="/static/images/YellowAlert.png" title="This player had all errors in this match." height=20px>';
    } else if (hasErrorsForPlayer[j]) {
      theMatchHTML += ' <img src="/static/images/WhiteAlert.png" title="This player had errors in this match." height=20px>';
    }
    
    theMatchHTML += '</td>'
    if ("goalValues" in theMatchJSON) {
      theMatchHTML += '<td class="padded" style="text-align: right;"><span ' + highlightAttribute + '>' + theMatchJSON.goalValues[j] + '</span></td>';
    } else {
      theMatchHTML += '<td class="padded"></td>';
    }
    theMatchHTML += '<td width="5px"></td></tr>';
  }
  theMatchHTML += '</table></td>';
  
  // Match page URL.
  var matchURL = theMatchJSON.apolloSpectatorURL.replace("http://matches.ggp.org/matches/", "");
  theMatchHTML += '<td class="padded"><a href="/matches/' + matchURL + '">View Match</a></td>';  

  // Signature badge.
  if ("apolloSigned" in theMatchJSON && theMatchJSON.apolloSigned) {
    theMatchHTML += '<td class="padded"><img src="/static/images/GreenLock.png" title="Match has a valid digital signature." height=20px></img></td>';
  } else {
    theMatchHTML += '<td class="padded"><img src="/static/images/RedLock.png" title="Match has no digital signature." height=20px></img></td>';
  }
  
  // Warning badge.
  if (allErrors) {
    theMatchHTML += '<td class="padded"><img src="/static/images/OrangeAlert.png" title="Every player had all errors during this match." height=20px></img></td>';
  } else if (allErrorsForSomePlayer) {
    theMatchHTML += '<td class="padded"><img src="/static/images/YellowAlert.png" title="At least one player had all errors during this match." height=20px></img></td>';
  } else if (hasErrors) {
    theMatchHTML += '<td class="padded"><img src="/static/images/WhiteAlert.png" title="Players had errors during this match." height=20px></img></td>';
  } else {
    theMatchHTML += '<td></td>';
  }

  theMatchHTML += '<td width="5px"></td>';
  return theMatchHTML + "</tr>";
}

function translateRepositoryCodename(x) {
  return x.replace("base/", "http://games.ggp.org/games/");
}
function translateRepositoryIntoCodename(x) {
  return x.replace("http://games.ggp.org/games/", "base/");
}

// The value "90bd08a7df7b8113a45f1e537c1853c3974006b2" is the hashed public key for the Apollo server.
// We request statistics for all of the matches which are signed using the Apollo server's key.
var hashedApolloPK = "90bd08a7df7b8113a45f1e537c1853c3974006b2";