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
// - driver uses these events to continue after component restart
// - a timeout is used in case latency event not received
// Steps:
// - stop/start random container target from N candidate containers
// - wait for long latency indicating recovery from new process, or timeout
// - wait a random time and repeat

let action = 'kill';
let enable = false;
let target;
let path = require('path');
let fork = require('child_process').fork;
let spawnSync = require('child_process').spawnSync;
let timeout;
let child;
let docker = "/usr/bin/docker";

function rndTarget() {
  var ti = Math.floor(Math.random() * targets.length);
  return targets[ti];
}

function rndsleep() {
  rsleep = 30 * Math.random();
  return 60 + Math.trunc(rsleep);
}

// support testing from simulators console file
//   no waiting as simulated output will continue at constant rate
async function doit(dsleep) {
  if (!process.env.FEEDFILE) {
    await new Promise(resolve => setTimeout(resolve, (dsleep * 1000)));
  }
  var timestamp = new Date().toLocaleString('en-US', { hour12: false });
  console.log(timestamp+' '+docker+' '+action+' '+target);
  if (!process.env.FEEDFILE) {
    var doitx = spawnSync(docker,[action,target]);
  }
  // set next action; if next=start, it will be done [almost] immediately
  if ( action == "kill" ) {
    action = "start";
    timeout = setTimeout(stopWaiting, 1000);
  } else {
    action = "kill";
    timeout = setTimeout(stopWaiting, 60 * 1000);
  }
  enable = true;
}

// Restart after kill or give up on latency event
function stopWaiting() {
  if ( action == "kill" ) {
    console.log("Timed out waiting for recovery event. Continuing");
  }
  if ( enable ) {
    if ( action == "kill" ) {
      // pick a new container
      target = rndTarget();
    }
    // disable acting on another message until this action completes
    enable = false;
    doit(0);
  }
}

function forkChild() {
  const parser = __dirname+'/docker-fault-parser.js';
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
  const grepsevere = new RegExp("^.*\(SEVERE|ERROR\)", "m");
  var match = grepsevere.exec(message);
  if (match) {
    console.log('special child message:', message);
    return;
  }
  if ( timeout ) {
    clearTimeout(timeout);
  }
  if ( enable ) {
    console.log('child message:', message);
    var pmsleep = rndsleep();
    if ( action == "kill" ) {
      // pick a new container
      target = rndTarget();
    } else {
      // start up immediately
      pmsleep = 0;
    }
    // disable acting on another message until this action completes
//debug    console.log("  disable message trigger");
    enable = false;
    if (!process.env.FEEDFILE) {
      // let app run for a bit
      console.log('  '+action+' '+target+' in '+pmsleep);
    }
    // stop rnd container or start last container stopped
    doit(pmsleep);
  } else {
    console.log('  ignoring child message:', message);
  }
}

async function main () {
  //TODO if any containers are stopped ...
  // ... exit with message that all containers need to be up

  // check if running podman play kube
  var grepsim = new RegExp("^.*simulators.*$", "m");
  const { execSync } = require('child_process');
  var stdout = execSync(docker + " ps");
  var match = grepsim.exec(stdout.toString());
  var words = match[0].split(" ");
  simC = words[words.length - 1];
  if (simC.endsWith('_1')) {
    targets = ['reefer_rest_1','reefer-actors-1','reefer-singletons-1'];
  }
  else if (simC.endsWith('-1')) {
    targets = ['reefer-rest-1','reefer-actors-1','reefer-singletons-1'];
  }
  else {
    targets = ['reefer-rest','reefer-actors','reefer-singletons'];
  }

  // fork child parser
  forkChild();
  await new Promise(resolve => setTimeout(resolve, (1000)));

  // first action is to stop a container
  var msleep = rndsleep();
  target = rndTarget();
  console.log('  '+action+' '+target+' in '+msleep);
  doit(msleep);

}

main()

