function generateHeader(theDiv) {
    var theHTML = "";
    theHTML += '<center>';
    theHTML += '<table>';
    theHTML += '  <tr>';
    theHTML += '    <td width=30% align="right" valign="bottom"><a class=biglink>Games</a></td>';
    theHTML += '    <td width=15% align="center" valign="bottom"><a class=biglink href="/players/">Players</a></td>';
    theHTML += '    <td width=10% align="center"><a href="/"><img src="/static/images/ApolloGamingLogo.png" width=200 height=200></img></a></td>';
    theHTML += '    <td width=15% align="center" valign="bottom"><a class=biglink href="/matches/">Matches</a></td>';
    theHTML += '    <td width=30% align="left" valign="bottom"><a class=biglink>Stats</a></td>';
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
      loginHTML += " You are signed in, but you can <a href=\"" + loginState.logoutURL.replace("/REPLACEME", window.location.pathname) + "\">sign out</a> if you'd like.";
    } else {
      loginHTML += "Sign in using OpenID via ";
      for (var i in loginState.preferredOrder) {
        var providerName = loginState.preferredOrder[i];
        loginHTML += "<a href=\"" + loginState.providers[providerName].replace("/REPLACEME", window.location.pathname) + "\"><img src=\"/static/images/" + providerName + ".png\"></img></a> ";
      }
    }      
    document.getElementById('login_div').innerHTML = loginHTML;    
}