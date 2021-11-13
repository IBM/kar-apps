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
// Steps:
// - stop random node n from N worker nodes (currently N>1)
// - wait for recovery
// - restart node n
// - wait for long latency indicating recovery from new process

let action = 'stop';
let enable = true;
let node = 'k3d-workernode-1-0';
let nodes = ['k3d-workernode-1-0','k3d-workernode-2-0'];
let path = require('path');
let fork = require('child_process').fork;
let spawnSync = require('child_process').spawnSync;

function rndnode() {
  var oneOrZero = Math.floor(Math.random() * 2);
  return nodes[oneOrZero];
}

function rndsleep() {
  sleep = 20 * Math.random();
  return 10 + Math.trunc(sleep);
}

// support testing from simulators console file
// no waiting as simulated output will continue at constant rate
async function doit() {
  if (!process.env.FEEDFILE) {
    var sleep = rndsleep();
    console.log('  '+action+' '+node+' in '+sleep);
    await new Promise(resolve => setTimeout(resolve, (sleep * 1000)));
  }
  var timestamp = new Date().toLocaleString('en-US', { hour12: false });
  console.log(timestamp+' k3d node '+action+' '+node);
  if (!process.env.FEEDFILE) {
    var doitx = spawnSync('/usr/local/bin/k3d',['node',action,node]);
  }
  if ( action == "stop" ) {
    action = "start";
  } else {
    action = "stop";
  }
  console.log("  enable message trigger");
  enable = true;
}

async function main () {

  const parser = __dirname+'/k3d-fault-parser.js';
  const program = path.resolve(parser);
  const parameters = [3500];
  const options = {
    //stdio: [ 'pipe', 'pipe', 'pipe', 'ipc' ]
    stdio: [ 0, 1, 2, 'ipc' ]
  };

  console.log('parent: forking child '+parser);
  const child = fork(program, parameters, options);

  // first action is to stop a node
  doit();

  // process alerts from child
  const grepsevere = new RegExp("^.*SEVERE", "m");
  child.on('message', message => {
    var match = grepsevere.exec(message);
    if (match) {
      console.log('special child message:', message);
      return;
    }
    if ( enable ) {
      console.log('child message:', message);
      if ( action == "stop" ) {
        // pick a new node
        node = rndnode();
      }
      // disable actiing on another message until this action completes
      console.log("  disable message trigger");
      enable = false;
      // stop rnd node or start last node stopped
      doit();
    } else {
      console.log('  ignoring child message:', message);
    }
  });
    
}

main()

