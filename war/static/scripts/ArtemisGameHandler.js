// Requires StateMachine.js as a dependency.

var ArtemisGameHandler = {
  width: null,
  height: null,
  
  matchURL: null,
  
  vizDiv: null,
  gameDiv: null,
  
  rulesheet: null,  
  stylesheet: null,
  user_interface: null,
  
  state: null,
  myRole: null,
  machine: null,
  selectedMove: null,
  interfaceEnabled: null,
  
  parent: null,
  
  initialize: function (parent, myRole, gameDiv, matchURL, width, height) {  
    this.width = width;
    this.height = height;
    this.parent = parent;
    
    this.matchURL = matchURL;
    var matchData = ResourceLoader.load_json(matchURL);
    var gameURL = matchData.gameMetaURL;
    var metadata = parent.ResourceLoader.load_json(gameURL);

    if ("description" in metadata) {
        var description = ResourceLoader.load_raw(gameURL + metadata.description);
        var desc_div = document.getElementById('desc_div');
        if (desc_div) {
          desc_div.innerHTML = '<b>Game Description:</b> ' + description;
        }
    }    
    
    var rules_url = gameURL + metadata.rulesheet;
    var style_url = gameURL + metadata.stylesheet;
    var inter_url = gameURL + metadata.user_interface;

    this.myRole = myRole;
    this.gameDiv = gameDiv;

    var rule_compound = parent.ResourceLoader.load_rulesheet(rules_url);
    this.stylesheet = parent.ResourceLoader.load_stylesheet(style_url);
    this.rulesheet = rule_compound[1];

    this.emptyDiv(this.gameDiv);
    this.gameDiv.innerHTML = "<table><tr><td colspan=2><div id='game_viz_div'></div></td></tr><tr><td><div id=selected_move_div><b>Selected Move: </b></div></td><td align='right'><table><tr><td><button type='button' id='clear_move_button' disabled='true' onclick='gameHandler.clearMove()'>Clear Move</button></td><td><button type='button' id='select_move_button' disabled='true' onclick='gameHandler.submitMove()'>Submit Move</button></td></tr></table></td></tr><tr><td colspan=2></td></tr></table>";
    this.vizDiv = document.getElementById("game_viz_div");   

    // Attach keybindings for the buttons the Artemis interface renders
    if (!this.hasKeyBindings) {
      var thisRef = this;
      var oldkeydown = document.onkeydown;
      document.onkeydown = function(e) { thisRef.onkeydown(e); if (oldkeydown) oldkeydown(e); }
      this.hasKeyBindings = true;
    }
    
    this.machine = load_machine(rule_compound[0])    
    this.state = SymbolList.symbolListIntoArray(matchData.states[matchData.states.length-1]);
    
    if (myRole < 0 || myRole >= this.machine.get_roles().length) {
        this.emptyDiv(this.gameDiv);
        this.gameDiv.innerHTML = "<b>Game does not have player \"" + (myRole+1) + "\"";
        return;
    }
    
    this.user_interface = parent.ResourceLoader.load_js(inter_url);
    this.interface_enabled = true;
    this.renderCurrentState();

    // Create a callback that will update the match state based on
    // messages from the browser channel.
    var thisRef = this;
    function update_state_via_channel(channelMessage) {
      if (channelMessage.data != matchURL) {
        // Channel update notification was about a different match.
        // Ignore it -- it was meant for a different SpectatorView.
        return;
      }
      var newMatchData = JSON.parse(ResourceLoader.load_raw(matchURL));
      var newStateString = newMatchData.states[newMatchData.states.length-1];
      if (newStateString != SymbolList.arrayIntoSymbolList(gameHandler.state)) {
        gameHandler.state = SymbolList.symbolListIntoArray(newStateString);
        gameHandler.interface_enabled = true;
        gameHandler.renderCurrentState();
      }
    }

    // Make sure that we're registered to view this match.
    ResourceLoader.load_raw("//database.ggp.org/subscribe/match/" + matchURL + 'clientId=' + theChannelID);

    // Open a Browser Channel to the Spectator Server.
    // We will receive updates to the match state over this channel.
    // We share a global channel with anybody else interested in using
    // a channel to communicate with the spectator server.
    if (window.theGlobalChannel == undefined) {
      window.theGlobalChannel = new goog.appengine.Channel(theChannelToken);
      window.theGlobalChannelCallbacks = [];
      window.theGlobalChannel.open().onmessage = function (x) {
        for (var i = 0; i < window.theGlobalChannelCallbacks.length; i++) {
          window.theGlobalChannelCallbacks[i](x);
        }
      }
    }
    window.theGlobalChannelCallbacks.push(update_state_via_channel);    
  },
  
  resize: function (width, height) {
    this.width = width;
    this.height = height;
    //...
  },

  emptyDiv: function (divToClear) {
    var i;
    while (i = divToClear.childNodes[0]){
      if (i.nodeType == 1 || i.nodeType == 3){
        divToClear.removeChild(i);
      }
    }
  },

  renderCurrentState: function () {
    var gameOver = this.machine.is_terminal(this.state);
    var legals;
  
    if (!gameOver) {
      legals = this.machine.get_legal_moves(this.state, this.machine.get_roles()[this.myRole]);
      if (legals.length == 1) {
        this.interface_enabled = false;
      }
    }

    this.emptyDiv(this.vizDiv);
    this.parent.StateRenderer.render_state_using_xslt(this.state, this.stylesheet, this.vizDiv, this.width, this.height);  
  
    if (!gameOver && this.interface_enabled) {
      var inner_args = {};
      var game_parent = this;
      inner_args.viz_div = this.vizDiv;
      inner_args.legals = legals;
      inner_args.selection_callback = function selectionCallback(move) {
        if(!move) move = "";
        
        document.getElementById("clear_move_button").disabled = !move;
        document.getElementById("select_move_button").disabled = !move;

        game_parent.selectedMove = move;
        document.getElementById("selected_move_div").innerHTML = "<b>Selected Move: </b>" + move;
      }
      this.user_interface.attach(inner_args);
    } else {
      if (gameOver) {
        var goal = this.machine.get_goal(this.state, this.machine.get_roles()[this.myRole]);
        document.getElementById("selected_move_div").innerHTML = "<b>Game Over! Score: " + goal + "</b>";
      } else {
        document.getElementById("selected_move_div").innerHTML = "<b>Waiting for next state...</b>";
      }
      
      document.getElementById("select_move_button").disabled = true;
      document.getElementById("clear_move_button").disabled = true;
    }
  },
  
  hasKeyBindings: false,
  onkeydown: function (e) {
    if (e.which == 13) {
      gameHandler.submitMove();
    } else if (e.which == 27) {
      gameHandler.clearMove();
    }
  },  

  writeSingleGDL: function(x) {
    if (!(x instanceof Array)) return x;

    var s = "( ";
    for (var i = 0; i < x.length; i++) {
      s = s + this.writeSingleGDL(x[i]) + " ";
    }
    return s + ")";
  },

  writeGDL: function (x) {
    var s = "";
    for (var i = 0; i < x.length-1; i++) {
      s = s + this.writeSingleGDL(x[i]) + ",";
    }
    return s + this.writeSingleGDL(x[x.length-1]);
  },

  submitMove: function () {
    if(!this.selectedMove) return;

    document.getElementById("selected_move_div").innerHTML = "<b>Selected Move: </b>";

    if (isOnlyRoleWithLegals(this.myRole, this.machine, this.state)) {
      // Special case for alternating-play games, in which we can render the
      // updated state before we actually send the XHR to the server. This is
      // a significant improvement for the user experience for such games.
      var rand_moves = this.machine.get_random_joint_moves(this.state);
      rand_moves[this.myRole] = this.selectedMove;
      this.state = this.machine.get_next_state(this.state, rand_moves);

      var zXHR = "ResourceLoader.load_raw(\"play/" + this.writeGDL([this.selectedMove]) + "\")";
      this.selectedMove = null;
      this.interface_enabled = true;
      this.renderCurrentState();

      setTimeout(zXHR, 1);
    } else {
      ResourceLoader.load_raw("play/" + this.writeGDL([this.selectedMove]));        
      this.selectedMove = null;
      this.interface_enabled = false;
      this.renderCurrentState();
    }
  },
    
  clearMove: function () {
  	this.user_interface.clearSelection();
  }
}

function isOnlyRoleWithLegals (role, machine, state) {
    var roles = machine.get_roles();
    for (var i = 0; i < roles.length; i++) {
        if (i == role) continue;
        
        if (machine.get_legal_moves(state, roles[i]).length > 1)
            return false;
    }
    return true;
}

// NOTE: This function *must* define gameHandler as a global variable.
// Otherwise, sections of the above code will not work.
function load_artemis_player (myRole, gameDiv, matchURL, width, height) {  
  gameHandler = Object.create(ArtemisGameHandler);  
  gameHandler.initialize(this, myRole, gameDiv, matchURL, width, height);  
  return gameHandler;
}
