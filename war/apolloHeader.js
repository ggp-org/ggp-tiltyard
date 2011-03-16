var loginNascarHTML;
function generateHeader(theDiv) {
    var theHTML = "";
    theHTML += '<center>';
    theHTML += '<table style="width: 100%; border: 0; margin: 0; border-spacing: 0px 0px;">';
    theHTML += '  <tr>';
    theHTML += '    <td class="navbar" width=20% align="left" style="padding: 0;"><a href="/"><img width=200px src="/static/images/SolidSunSlice.png"></img></a></td>';
    theHTML += '    <td class="navbar" width=10% align="center" valign="middle"><a class=biglink href="/about/">About</a></td>';
    theHTML += '    <td class="navbar" width=10% align="center" valign="middle"><a class=biglink href="/games/">Games</a></td>';
    theHTML += '    <td class="navbar" width=10% align="center" valign="middle"><a class=biglink href="/players/">Players</a></td>';    
    theHTML += '    <td class="navbar" width=10% align="center" valign="middle"><a class=biglink href="/matches/">Matches</a></td>';
    theHTML += '    <td class="navbar" width=10% align="center" valign="middle"><a class=biglink href="/stats/">Stats</a></td>';
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