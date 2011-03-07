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
        loginNascarHTML += "<a href=\"" + loginState.providers[providerName].replace("/REPLACEME", window.location.pathname) + "\"><img src=\"/static/images/" + providerName + ".png\"></img></a> ";
      }
    }      
    document.getElementById('login_div').innerHTML = loginHTML;
}

function generatePlayerHTML(aPlayer) {
    var thePlayerHTML = '<table draggable="true" class="player" style="background-color:';
    if ("theURL" in aPlayer) {
        thePlayerHTML += '#CCEECC';
    } else {
        thePlayerHTML += '#DDDDDD';
    }
    thePlayerHTML += '"><tr>';
    thePlayerHTML += '<td width=5></td>';
    thePlayerHTML += '<td><table style="border-width: 2px; border-style: inset;" cellspacing=0 cellpadding=0><tr><td><img src="http://placekitten.com/g/50/50"/></tr></td></table></td>';
    thePlayerHTML += '<td width=5></td>';
    thePlayerHTML += '<td><a style="text-decoration:none; color: #222222;" href="/players/' + aPlayer.name + '"><font size=6><b>' + aPlayer.name + '</b></font><br>';
    if (aPlayer.visibleEmail.length > 0) {
        thePlayerHTML += '<tt>' + aPlayer.visibleEmail + '</tt>';
    } else {
        thePlayerHTML += '<i>Email address not listed.</i>';
    }
    thePlayerHTML += '</a></td>';
    thePlayerHTML += '<td width=5></td>';
    thePlayerHTML += '<td>';
    if (aPlayer.isEnabled) {
        thePlayerHTML += '<table class="enabled"><tr><td>Enabled!</td></tr></table>';
    } else {
        thePlayerHTML += '<table class="disabled"><tr><td>Disabled</td></tr></table>'; 
    }
    thePlayerHTML += '<br>';
    thePlayerHTML += '<table class="gdlversion"><tr><td>' + aPlayer.gdlVersion + '</td></tr></table>';
    thePlayerHTML += '</td></tr></table>';
    return thePlayerHTML;
}