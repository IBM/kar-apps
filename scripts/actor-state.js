#!/usr/bin/env node

//
// this tools dumps selected app data from Kar's persistent store
// requires npm install of yargs and redis
//

const redis = require("redis");
const client = redis.createClient({port: process.env.REDIS_PORT,
				   host: process.env.REDIS_HOST,
				   password: process.env.REDIS_PASSWORD});
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



