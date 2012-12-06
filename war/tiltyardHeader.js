function generateLoginPanel(theDiv) {
    var theHTML = "";
    theHTML += '<center>';
    theHTML += '<table style="width: 65%; border: 1px; bgcolor: rgb(160,160,160);">';
    theHTML += '  <tr class="navbarTop">';
    theHTML += '    <td><div class="login" id="login_div"></div></td>';
    theHTML += '  </tr>';
    theHTML += '  <tr id="navBuffer" class="navbarBottom">'; 
    theHTML += '    <td></td>';
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
      loginHTML = "Sign in using OpenID via ";
      for (var i in loginState.preferredOrder) {
        var providerName = loginState.preferredOrder[i];
        loginHTML += "<a rel=\"nofollow\" href=\"" + loginState.providers[providerName].replace("/REPLACEME", window.location.pathname) + "\"><img src=\"/static/images/" + providerName + ".png\"></img></a> ";
      }
    }      
    document.getElementById('login_div').innerHTML = loginHTML;
}