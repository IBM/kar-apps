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

// to keep code engine happy

const express = require('express')
const app = express()
app.get('/health', (req, res) => {
  console.log('I am healthy')
  res.send('I am healthy!')
})
console.log('listening on '+process.env.KAR_APP_PORT)
app.listen(process.env.KAR_APP_PORT, '0.0.0.0')