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

// output input file line by line with specified delay or default 100 ms

const fs = require('fs');
const readline = require('readline');

async function processLineByLine(afile) {
  const fileStream = fs.createReadStream(afile);

  const rl = readline.createInterface({
    input: fileStream,
    crlfDelay: Infinity
  });
  // Note: we use the crlfDelay option to recognize all instances of CR LF
  // ('\r\n') in input.txt as a single line break.

  for await (const line of rl) {
    // Each line in input.txt will be successively available here as `line`.
    process.stdout.write(`${line}`+'\n');
    process.stdout.on('error', function(err) {
      process.exit(0);
    });
    await new Promise(resolve => setTimeout(resolve, delay))
  }
}

let delay = 100;
const args = process.argv.slice(2);
if (args[1]) {
  delay = args[1];
}
console.log('feedfile delay = '+delay+'ms');
processLineByLine(args[0]);
