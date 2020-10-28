//
// this kar client monitors simulated order generation
//   delay = current number of seconds allowed to run a day's work
//   orderctl = {order target, order window, order updates}
//   orderstats:
//     successful = orders generated
//     mean = average order latency in ms, excluding outliers
//     stddev = distribution of order latencies, excluding outliers
//     max = max latency, including outliers
//     outliers = order latencies > instantanious mean + 5*stddev
//     failed = failed order requests
//     missed = number of order events that could not be executed within a "day"

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
  var sleep=60
  if ( process.argv[2] ) {
    sleep=process.argv[2]
  }
    
  while (true) {
    const unitdelay = await fetch(url('simservice', 'simulator/getunitdelay'), {
      method: 'GET',
      headers: { 'Content-Type': 'application/json' }
    })
    const objdly = await unitdelay.json()

    const orderctl = await fetch(url('simservice', 'simulator/getordercontrols'), {
      method: 'GET',
      headers: { 'Content-Type': 'application/json' }
    })
    const objctl = await orderctl.json()

    const orderstats = await fetch(url('simservice', 'simulator/getorderstats'), {
      method: 'GET',
      headers: { 'Content-Type': 'application/json' }
    })
    const objstats = await orderstats.json()

    console.log('delay:{'+JSON.stringify(objdly)+'} orderctl:{'+objctl.ordertarget+','+objctl.orderwindow+','+objctl.orderupdates+
		'} orderstats:'+JSON.stringify(objstats))
    await new Promise(resolve => setTimeout(resolve, sleep*1000))
  }
}

// invoke main
main()
