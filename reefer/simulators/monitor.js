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

//
// this kar client monitors simulated order generation
//   delay = current number of seconds allowed to run a day's work
//   orderctl = {order target, order window, order updates}
//   orderstats:
//     good = number orders generated
//     mean = average order latency in ms, excluding outliers
//     stddev = distribution of order latencies, excluding outliers
//     max = max latency, including outliers
//     thresh = outlier threshold; 0 = calculate dynamically
//     outliers = order latencies > instantanious mean + 5*stddev
//     bad = failed order requests
//     miss = number of order groups that could not be started within a "day"

// retry http requests up to 10 times over 10s
const fetch = require('fetch-retry')(require('node-fetch'), { retries: 10 })

if (!process.env.KAR_RUNTIME_PORT) {
  console.error('KAR_RUNTIME_PORT must be set. Aborting.')
  process.exit(1)
}

// request url for a given KAR service and route on that service
function url (service, route) {
  return `http://127.0.0.1:${process.env.KAR_RUNTIME_PORT}/kar/v1/service/${service}/call/${route}`
}

async function main () {

  var argv = require('yargs/yargs')(process.argv.slice(2))
    .usage('Usage: $0 [--delay <int>] [--thresh <int>] [--reset <int>]')
    .version(false)
    .help('help').alias('help', 'h')
    .options({
      delay: {
	alias: 'd',
	description: "delay between reports"
      },
      thresh: {
	alias: 't',
	description: 'outlier threshold'
      },
      reset: {
	alias: 'r',
	description: 'reset stats after specified updates'
      },
      counts: {
	alias: 'c',
	description: 'output order & reefer counts'
      }
    })
    .argv;

  var sleep=60
  if ( process.env.ORDERSTATS_DELAY ) {
    sleep=process.env.ORDERSTATS_DELAY
  }
  if (argv.delay) {
    sleep=argv.delay
  }

  var thresh=0
  var resetthresh=0
  if ( process.env.ORDERSTATS_THRESHOLD ) {
    thresh=process.env.ORDERSTATS_THRESHOLD
  }
  if (argv.thresh) {
    thresh=argv.thresh
  }
  if ( thresh > 0 ) {
    console.log('resetting order stats and setting outlier threshold to '+thresh+' ms')
    resetthresh=1
  }

  var reset=0
  var rcd=0
  if ( process.env.ORDERSTATS_RESET ) {
    reset=process.env.ORDERSTATS_RESET
  }
  if (argv.reset) {
    reset=argv.reset
  }
  if ( reset > 0 ) {
    console.log('resetting order stats after every '+reset+' updates')
  }

  var counts=0
  if ( process.env.ORDERSTATS_COUNTS ) {
    counts=process.env.ORDERSTATS_COUNTS
  }
  if (argv.counts) {
    counts=argv.counts
  }

  // reporting loop
  while (true) {
    this.startTime = Date.now()
    const unitdelay = await fetch(url('simservice', 'simulator/getunitdelay'), {
      method: 'GET',
      headers: { 'Content-Type': 'application/json' }
    })
    const restext = await unitdelay.text()
    // at startup liberty may respond with garbage string before app is ready to serve.
    // a valid unitdelay response will never include a space.
    if ( restext.includes(' ') ) {
      await new Promise(resolve => setTimeout(resolve, 1000))
      continue
    }
    const objdly = JSON.parse(restext)

    const orderctl = await fetch(url('simservice', 'simulator/getordercontrols'), {
      method: 'GET',
      headers: { 'Content-Type': 'application/json' }
    })
    const objctl = await orderctl.json()

    if ( resetthresh > 0 ) {
      resetthresh=0
      const oktext = await fetch(url('simservice', 'simulator/resetorderstats'), {
	method: 'POST',
	body: JSON.stringify({ value: new Number(thresh) }),
	headers: { 'Content-Type': 'application/json' }
      })
      const objok = await oktext.json()
    }

    const orderstats = await fetch(url('simservice', 'simulator/getorderstats'), {
      method: 'GET',
      headers: { 'Content-Type': 'application/json' }
    })
    const objstats = await orderstats.json()

    var output = 'delay:{'+JSON.stringify(objdly)+'} orderctl:{'+objctl.ordertarget+','+
	objctl.orderwindow+','+objctl.orderupdates+'} orderstats:'+JSON.stringify(objstats)

    if ( counts > 0 ) {
      const ordercounts = await fetch(url('reeferservice', 'orders/stats'), {
	method: 'GET',
	headers: { 'Content-Type': 'application/json' }
      })
      const objcounts = await ordercounts.json()

      const reefercounts = await fetch(url('reeferservice', 'reefers/stats'), {
	method: 'GET',
	headers: { 'Content-Type': 'application/json' }
      })
      const objrefcounts = await reefercounts.json()

      output = output+'\n   order counts:'+JSON.stringify(objcounts)+
	              '\n   reefer counts:'+JSON.stringify(objrefcounts)
    }

    console.log(output)

    // check if auto reset time
    if ( reset > 0 ) {
      if ( rcd > 0 ) {
	rcd -= 1
      }
      if ( rcd == 0 ) {
	rcd = reset
	const oktext = await fetch(url('simservice', 'simulator/resetorderstats'), {
	  method: 'POST',
	  body: JSON.stringify({ value: new Number(thresh) }),
	  headers: { 'Content-Type': 'application/json' }
	})
	const objok = await oktext.json()
      }
    }

    await new Promise(resolve => setTimeout(resolve, (sleep*1000)-(Date.now()-this.startTime)))
  }
}

// invoke main
main()
