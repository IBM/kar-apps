// to keep code engine happy

const express = require('express')
const app = express()
app.get('/health', (req, res) => {
  console.log('I am healthy')
  res.send('I am healthy!')
})
console.log('listening on '+process.env.KAR_APP_PORT)
app.listen(process.env.KAR_APP_PORT, '0.0.0.0')
