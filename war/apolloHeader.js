var loginNascarHTML;
function generateHeader(theDiv) {
    var theHTML = "";
    theHTML += '<center>';
    theHTML += '<table style="width: 100%; border: 0; margin: 0; border-spacing: 0px 0px;">';
    theHTML += '  <tr>';
    theHTML += '    <td class="navbar" width=20% align="left" style="padding: 0;"><a href="/"><img width=200px src="/static/images/SolidSunSlice.png"></img></a></td>';
    theHTML += '    <td class="navbar" width=10% align="center" valign="middle"><a class=biglink href="/about/">About</a></td>';
    theHTML += '    <td class="navbar" width=10% align="center" valign="middle"><a class=biglink>Games</a></td>';
    theHTML += '    <td class="navbar" width=10% align="center" valign="middle"><a class=biglink href="/players/">Players</a></td>';    
    theHTML += '    <td class="navbar" width=10% align="center" valign="middle"><a class=biglink href="/matches/">Matches</a></td>';
    theHTML += '    <td class="navbar" width=10% align="center" valign="middle"><a class=biglink>Stats</a></td>';
    theHTML += '    <td class="navbar" width=30% align="right" valign="middle"><div class="login" id="login_div"> </div></td>';
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
        thePlayerHTML += '#CCEECC';
    } else {
        thePlayerHTML += '#DDDDDD';
    }
    thePlayerHTML += '">';
    thePlayerHTML += generatePlayerInnerHTML(aPlayer);
    thePlayerHTML += '</table>';    
    return thePlayerHTML;
}

var theRecordedPlayers = {};
function generatePlayerInnerHTML(aPlayer) {
    theRecordedPlayers[aPlayer.name] = aPlayer;
    
    var thePlayerHTML = "";
    thePlayerHTML += '<tr><td width=5></td>';
    thePlayerHTML += '<td><a style="text-decoration:none; color: #222222;" href="/players/' + aPlayer.name + '"><table style="border-width: 2px; border-style: inset;" cellspacing=0 cellpadding=0><tr><td><img src="http://placekitten.com/g/50/50"/></tr></td></table></a></td>';
    thePlayerHTML += '<td width=5></td>';
    thePlayerHTML += '<td><a style="text-decoration:none; color: #222222;" href="/players/' + aPlayer.name + '"><font size=6><b>' + aPlayer.name + '</b></font></a>';
    thePlayerHTML += '<div id=player_' + aPlayer.name + '_email>'; 
    if (aPlayer.visibleEmail.length > 0) {
        thePlayerHTML += '<tt>' + aPlayer.visibleEmail + '</tt>';
    } else {
        thePlayerHTML += '<i>Email address not listed.</i>';
    }
    thePlayerHTML += '</div></td>';
    thePlayerHTML += '<td width=5></td>';
    thePlayerHTML += '<td>';
    if (aPlayer.isEnabled) {
        thePlayerHTML += '<table class="enabled"><tr><td>Enabled!</td></tr></table>';
    } else {
        thePlayerHTML += '<table class="disabled"><tr><td>Disabled</td></tr></table>'; 
    }
    thePlayerHTML += '<br>';
    thePlayerHTML += '<table class="gdlversion"><tr><td>' + aPlayer.gdlVersion + '</td></tr></table>';
    thePlayerHTML += '</td></tr>';
    if ("theURL" in aPlayer) {
        thePlayerHTML += '<tr><td width=5></td>';
        thePlayerHTML += '<td><b>URL:</b></td><td width=5></td>';
        thePlayerHTML += '<td><div id=player_' + aPlayer.name + '_url>';
        thePlayerHTML += '<tt>' + aPlayer.theURL + '</tt></div></td><td width=5></td>';
        thePlayerHTML += '<td><div id=player_' + aPlayer.name + '_button><button onclick=\'clickedEditForPlayer("' + aPlayer.name + '")\' type="Button">Edit</button></div></td></tr>'; 
    }
    return thePlayerHTML;
}

function clickedEditForPlayer (playerName) {
    var aPlayer = theRecordedPlayers[playerName];
    var urlDiv = document.getElementById("player_" + playerName + "_url");
    var emailDiv = document.getElementById("player_" + playerName + "_email");
    var buttonDiv = document.getElementById("player_" + playerName + "_button");
    
    urlDiv.innerHTML = '<input type="text" size="22" id="player_' + playerName + '_url_field" value="' + aPlayer.theURL + '">';
    emailDiv.innerHTML = '<input type="text" size="22" id="player_' + playerName + '_email_field" value="' + aPlayer.visibleEmail + '">';
    buttonDiv.innerHTML = '<button onclick=\'clickedEditDoneForPlayer("' + aPlayer.name + '")\' type="Button">Done!</button>';
}

function clickedEditDoneForPlayer (playerName) {
    var aPlayer = theRecordedPlayers[playerName];
    
    var newURL = document.getElementById("player_" + playerName + "_url_field").value;
    var newEmail = document.getElementById("player_" + playerName + "_email_field").value;
    aPlayer.theURL = newURL;
    aPlayer.visibleEmail = newEmail;

    // TODO: Make this asynchronous?
    var bPlayer = JSON.parse(ResourceLoader.post_raw("/data/updatePlayer", JSON.stringify(aPlayer)));    

    var theTable = document.getElementById("player_" + playerName + "_table");
    theTable.innerHTML = generatePlayerInnerHTML(bPlayer);
}