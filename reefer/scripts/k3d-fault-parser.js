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

async function main() {
  const args = process.argv.slice(2);

  var thresh = 5000;
  if ( args[0] != null ) {
    thresh = args[0];
  }
  const timestamp = new Date().toLocaleString('en-US', { hour12: false });
  [date,time] = timestamp.split(" ");
  console.log("Using duration threshold="+thresh+" and ignoring dates earlier than "+date+time);
  fs = require('fs');
  let writeStream = fs.createWriteStream('k3d-fault-run-'+date.replace(/\//g,'-')+time);
 
//  var child = require('child_process').spawn('node',['feedfile.js', '/home/eddie/temp/testing/reefer_simulators.console']);
//  var child = require('child_process').spawn('/usr/bin/docker',['logs', '-f', 'reefer_simulators_1']);
  var grepsim = new RegExp("^.*simulators.*$", "m");
  const { execSync } = require('child_process');
  const stdout = execSync("/usr/bin/kubectl get po");
  // console.log(stdout.toString());
  var match = grepsim.exec(stdout.toString());
  if (!match) {
    console.log("can't see a simulator pod running");
    process.exit();
  }
  var pods = match[0].split(" ");
  console.log("scanning output from "+pods[0]);  
	
  var child = require('child_process').spawn('/usr/bin/kubectl',['logs', '-f', pods[0], '-c', 'app']);
  const rl = require('readline').createInterface({ input: child.stdout });

  const grepper = new RegExp("^.*order latency outlier", "m");
  timenow = Date.parse(timestamp);

  rl.on('line', function(line) {
    writeStream.write(line+'\n');    
    var match = grepper.exec(line);
    if (match) {
      var words = line.trim().split(" ");
      teststamp = Date.parse(words[0].replace('[','')+" "+words[1].replace(']',''));
      if ( timenow > teststamp ) {
        // ignore old data
       return;
      }
//        console.log(line);
      var duration = words[words.length-1];
      if (Number(duration) > thresh) {
//        console.log(words[0]+" "+words[1]+" "+duration);
        if (process.send) {
          process.send(words[0]+" "+words[1]+" "+duration);
        }
      }
    } else {
      //TODO look for SEVERE messages, if order failed for good reason, alert parent
    }
  });

  child.on('close', function() {
    process.exit();
  });
}
    
main()
          
