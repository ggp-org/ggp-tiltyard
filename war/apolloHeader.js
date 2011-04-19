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
    
    var thePlayerHTML = "";
    thePlayerHTML += '<tr><td width=5></td>';
    thePlayerHTML += '<td width=60><a style="text-decoration:none; color: #222222;" href="/players/' + aPlayer.name + '"><table style="border-width: 2px; border-style: inset;" cellspacing=0 cellpadding=0><tr><td><img width=50 height=50 src="http://placekitten.com/g/50/50"/></tr></td></table></a></td>';
    thePlayerHTML += '<td width=5></td>';
    thePlayerHTML += '<td width=255><a style="text-decoration:none; color: #222222;" href="/players/' + aPlayer.name + '"><font size=6><b>' + aPlayer.name + '</b></font></a>';
    thePlayerHTML += '<div id=player_' + clip(aPlayer.name, 15) + '_email>'; 
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

function renderDateTime(d) {    
  var suffix = "AM";
  var hours = d.getHours()
  var minutes = d.getMinutes()  
  if (hours >= 12) { suffix = "PM"; hours = hours - 12; }
  if (hours == 0) { hours = 12; }
  if (minutes < 10) { minutes = "0" + minutes; }

  var out = "at ";
  out += hours + ":" + minutes + " " + suffix + " on ";
  out += (d.getMonth()+1) + "/" + d.getDate() + "/" + d.getFullYear();
  return out;
}

function renderMatchEntry(theMatchJSON, theOngoingMatches) {
  var hasErrors = false;
  var hasErrorsForPlayer = [];
  for (var i = 0; i < theMatchJSON.gameRoleNames.length; i++) {
    hasErrorsForPlayer.push(false);
  }
  if ("errors" in theMatchJSON) {
    for (var i = 0; i < theMatchJSON.errors.length; i++) {
      for (var j = 0; j < theMatchJSON.errors[i].length; j++) {
        if (theMatchJSON.errors[i][j] != "") {
          hasErrors = true;
          hasErrorsForPlayer[j] = true;
        }
      }
    }
  }
    
  var theMatchHTML = "";
  var theDate = new Date(theMatchJSON.startTime);
  var matchURL = theMatchJSON.apolloSpectatorURL.replace("http://matches.ggp.org/matches/", "");
  theMatchHTML += '<a href="/matches/' + matchURL + '">Match</a> started ';
  theMatchHTML += renderDateTime(theDate);
  theMatchHTML += ' with {';
  for (var j = 0; j < theMatchJSON.apolloPlayers.length; j++) {
    theMatchHTML += '<a href="/players/' + theMatchJSON.apolloPlayers[j] + '">' + theMatchJSON.apolloPlayers[j] + '</a>';
    if (hasErrorsForPlayer[j]) {
      theMatchHTML += '<b><font color=#FFCC00>*</font></b>';
    }
    if (j < theMatchJSON.apolloPlayers.length - 1) {
      theMatchHTML += ', ';
    }
  }
  theMatchHTML += '}: <a href="' + theMatchJSON.apolloSpectatorURL + 'viz.html">Spectator View</a>. ';
  if (hasErrors) {
    theMatchHTML += '<b><font color=#FFCC00>(Errors)</font></b> ';
  }
  if (theOngoingMatches.indexOf(theMatchJSON.apolloSpectatorURL) >= 0) {
    theMatchHTML += '<b>(Ongoing!)</b>';
  }  
  return theMatchHTML;
}

function translateRepositoryCodename(x) {
  return x.replace("base/", "http://games.ggp.org/games/");
}
function translateRepositoryIntoCodename(x) {
  return x.replace("http://games.ggp.org/games/", "base/");
}