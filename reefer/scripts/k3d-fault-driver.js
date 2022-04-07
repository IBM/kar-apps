/*
 * Copyright IBM Corporation 2020,2021
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Driver logic that creates fault events
// - a child process parses the simulator output looking for above threshold order latency events
// - driver uses these events to continue to next step
// - a timeout is used in case latency event not received
// Steps:
// - stop random node n from N worker nodes
// - wait for recovery
// - restart node n
// - wait for long latency indicating recovery from new process, or timeout

let action, enable, timeout;
let nodes, node, node2;
let pairkill, singlenode;

let path = require('path');
let fork = require('child_process').fork;
let spawnSync = require('child_process').spawnSync;
let child;

function rndnode() {
  var target = Math.floor(Math.random() * nodes.length);
  return nodes[target];
}

function rndsleep() {
  sleep = 30 * Math.random();
  return 30 + Math.trunc(sleep);
}

// support testing from simulators console file
//   no waiting as simulated output will continue at constant rate
async function doit(sleep) {
  if (!process.env.FEEDFILE) {
    await new Promise(resolve => setTimeout(resolve, (sleep * 1000)));
  }
  var timestamp = new Date().toLocaleString('en-US', { hour12: false });
  console.log(timestamp+' k3d node '+action+' '+node);
  if (!process.env.FEEDFILE) {
    // do the next action
    var doitx = spawnSync('/usr/local/bin/k3d',['node',action,node]);

    // if (doitx.stderr) {
    //   console.log(Error(doitx.stderr));
    //   process.exitCode = 1;
    // }

    // if pairkill & stop, stop the paired node
    if ( action == "stop" && pairkill) {
      node2 = rndnode();
      // pick paired node
      while ( node2 == node ) {
        node2 = rndnode();
      }
      pairTime = 10 + Math.trunc(10 * Math.random());
      await new Promise(resolve => setTimeout(resolve, (pairTime * 1000)));
      timestamp = new Date().toLocaleString('en-US', { hour12: false });
      console.log(timestamp+' k3d node '+action+' '+node2);
      doitx = spawnSync('/usr/local/bin/k3d',['node',action,node2]);
    }
    // if pairkill & start, start the paired node
    if ( action == "start" && pairkill) {
      console.log(timestamp+' k3d node '+action+' '+node2);
      doitx = spawnSync('/usr/local/bin/k3d',['node',action,node2]);
    }

    if ( action == "stop" && singlenode ) {
      // schedule restart in 30 second
      timeout = setTimeout(stopWaiting, 30*1000);
    } else {
      timeout = setTimeout(stopWaiting, 90*1000);
    }
  }

  // set next action
  if ( action == "stop" ) {
    action = "start";
  } else {
    action = "stop";
  }

  // enable message trigger
  enable = true;
}

// Restart singlenode or give up on latency event
function stopWaiting() {
  if ( !singlenode || action == "stop" ) {
    console.log("Timed out waiting for recovery event. Continuing");
  }
  if ( enable ) {
    if ( action == "stop" ) {
      // pick a new node
      node = rndnode();
    }
    // disable actiing on another message until this action completes
    enable = false;
    // stop rnd node or start last node stopped
    doit(0);
  }
}

function forkChild() {
  const parser = __dirname+'/k3d-fault-parser.js';
  const program = path.resolve(parser);
  const parameters = [5000];
  const options = {
    stdio: [ 0, 1, 2, 'ipc' ]
  };

  console.log('parent: forking child '+parser);
  child = fork(program, parameters, options);
  child.on('message', message => {
    processMessage(message);
  });

  child.on('exit', (code) => {
    console.log('simulator stream terminated with code ' + `${code}`);
    // if testing with filefeed
    if (process.env.FEEDFILE) {
      process.exit(0);
    } else {
      // sometimes kubectl logs breaks and new stream must be reestablished
      forkChild();
    }
  });
}

// process alerts from child
function processMessage(message) {
  const grepsevere = new RegExp("^.*SEVERE", "m");
  var match = grepsevere.exec(message);
  if (match) {
    console.log('special child message:', message);
    return;
  }
  if ( timeout ) {
    clearTimeout(timeout);
  }
  var pmsleep = 0;
  if ( enable ) {
    console.log('child message:', message);
    if ( action == "stop" ) {
      // pick a new node
      node = rndnode();
    }
    // disable actiing on another message until this action completes
    enable = false;
    if (!process.env.FEEDFILE) {
      // let app run for a bit
      pmsleep = rndsleep();

      if ( pairkill ) {
        console.log('  '+action+' two nodes in '+pmsleep);
      } else {
        console.log('  '+action+' '+node+' in '+pmsleep);
      }
    }
    // stop rnd node or start last node(s) stopped
    doit(pmsleep);
  } else {
    console.log('  ignoring child message:', message);
  }
}

async function main () {

  action = 'stop';
  enable = false;
  pairkill = false;
  singlenode = false;

  if ( process.env.SINGLENODE_FAULTS ) {
    singlenode=true
  }
  if ( process.env.PAIRKILL_FAULTS ) {
    pairkill=true
  }
  if ( singlenode && pairkill ) {
    console.error('enable singlenode OR pairkill OR neither')
    process.exit(1)
  }

  nodes = ['k3d-workernode-1-0','k3d-workernode-2-0'];
  if ( singlenode ) {
    nodes = ['k3d-workernode-1-0'];
    console.log('running singlenode mode ...');
  } else if ( pairkill ) {
    nodes = ['k3d-workernode-1-0','k3d-workernode-2-0','k3d-workernode-3-0'];
    console.log('running pairkill mode ...');
  } else {
    console.log('running normal mode ...');
  }
  console.log('... assuming K3D cluster has {rest,actor,singleton} pods only running on following nodes: '+nodes);
  console.log('    Confirm using "kubectl get po -o wide"');

  //TODO check if expected number of nodes are running

  // fork child parser
  forkChild();
  await new Promise(resolve => setTimeout(resolve, (1000)));

  // first action is to stop a node
  node = rndnode();
  if (!process.env.FEEDFILE) {
    var sleep = 10;
    if ( pairkill ) {
      console.log('  '+action+' two nodes in '+sleep);
    } else {
      console.log('  '+action+' '+node+' in '+sleep);
    }
  }
  doit(sleep);

}

main()

