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

#!/usr/bin/env node

//
// this tools dumps selected app data from Kar's persistent store
// requires npm install of yargs and redis
//

const redis = require("redis");
var client = null;
if (process.env.REDIS_ENABLE_TLS) {
  const ca = Buffer.from(process.env.REDIS_CERTIFICATE, 'base64').toString('utf-8')
  const tls = {ca};
  const redis_url = 'rediss://admin:' + process.env.REDIS_PASSWORD + '@' +
	process.env.REDIS_HOST + ':' + process.env.REDIS_PORT;
  client = redis.createClient(redis_url, {tls});
}
else {
  client = redis.createClient({port: process.env.REDIS_PORT,
 			       host: process.env.REDIS_HOST,
 			       password: process.env.REDIS_PASSWORD});
}
const util = require('util');

const hgetall = util.promisify(client.hgetall).bind(client);
async function hgetallAsync(arg) {
    try {
      const hash = await hgetall(arg);
      Object.keys(hash).forEach(function (key) {
	console.log(key + '\t' + hash[key])
      });
      process.exit(0);
    } catch (err) {
      console.log('Error', err);
      process.exit(0);
    }
}

const keys = util.promisify(client.keys).bind(client);
async function keysAsync(arg) {
    try {
      const array = await keys(arg);
      array.forEach(function (item, index) {
	var pieces = item.split('_')
	console.log(pieces[4] + '\t' +pieces[5])
      });
      process.exit(0);
    } catch (err) {
      console.log('Error', err);
      process.exit(0);
    }
}

// start of main

client.on("error", function(error) {
  console.error(error);
});

var argv = require('yargs/yargs')(process.argv.slice(2))
    .usage('Usage: $0 --app <kar-app> --type <actor-type> [--id <actor-id>]')
    .help('help').alias('help', 'h')
    .version('version', '0.0.1').alias('version', 'V')
    .options({
      app: {
	alias: 'a',
	description: "Kar application",
	requiresArg: true,
	required: true
      },
      type: {
	alias: 't',
	description: 'Actor type or type-prefix or \'*\'',
	requiresArg: true,
	required: true
      },
      id: {
	alias: 'i',
	description: 'Actor id  (if specified, type must be explicit)'
      }
    })
    .argv;

if(argv.id) {
  if (argv.type.includes('*') ) {
    console.log('"type" argument cannot include wildcard when "id" is specified')
    process.exit(1)
  }
  hgetallAsync('kar_' + argv.app + '_main_state_' + argv.type + '_' + argv.id);
}
else {
  keysAsync('kar_' + argv.app + '_main_state_' + argv.type+'*');
}



