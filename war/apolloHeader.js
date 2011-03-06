var loginNascarHTML;
function generateHeader(theDiv) {
    var theHTML = "";
    theHTML += '<center>';
    theHTML += '<table style="width: 100%; border: 0; margin: 0; border-spacing: 0px 0px;">';
    theHTML += '  <tr>';
    theHTML += '    <td width=30% align="right" valign="bottom"><a class=biglink>Games</a></td>';
    theHTML += '    <td width=15% align="center" valign="bottom"><a class=biglink href="/players/">Players</a></td>';
    theHTML += '    <td width=10% align="center" style="padding: 0;"><a href="/"><img width=200 src="/static/images/SolidSunSection2.png"></img></a></td>';
    theHTML += '    <td width=15% align="center" valign="bottom"><a class=biglink href="/matches/">Matches</a></td>';
    theHTML += '    <td width=30% align="left" valign="bottom"><a class=biglink>Leaderboard</a></td>';
    theHTML += '  </tr>';
    theHTML += '  <tr>';
    theHTML += '    <td colspan=5 align="center" class="login">';
    theHTML += '      <div id="login_div"> </div>';
    theHTML += '    </td>';
    theHTML += '  </tr>';
    theHTML += '</table>';
    theHTML += '</center>';
    theDiv.innerHTML = theHTML;
    
    var loginState = ResourceLoader.load_json('/data/login');
    var loginHTML = "";
    if (loginState.loggedIn) {
      if (loginState.nickname.length > 0) {
        loginHTML += "Hello, " + loginState.nickname + "! ";
      } else {
        loginHTML += "Hello! ";
      }
      loginHTML += " You are signed in, but you can <a class=\"darklink\" href=\"" + loginState.logoutURL.replace("/REPLACEME", window.location.pathname) + "\">sign out</a> if you'd like.";
    } else {
      loginHTML += " <a class=\"darklink\" href=\"javascript: document.getElementById('login_div').innerHTML = loginNascarHTML;\">Sign in</a> using OpenID to add your player to the match schedule.";
      loginNascarHTML = "Sign in using OpenID via ";
      for (var i in loginState.preferredOrder) {
        var providerName = loginState.preferredOrder[i];
        loginNascarHTML += "<a href=\"" + loginState.providers[providerName].replace("/REPLACEME", window.location.pathname) + "\"><img src=\"/static/images/" + providerName + ".png\"></img></a> ";
      }
    }      
    document.getElementById('login_div').innerHTML = loginHTML;    
}

function generatePlayerHTML(aPlayer) {
    var thePlayerHTML = '<table class="player" style="background-color:';
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