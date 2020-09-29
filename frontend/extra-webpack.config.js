const webpack = require('webpack')

module.exports = {
   plugins: [
      new webpack.DefinePlugin({
         'process.env': {
	     REEFER_REST_HOST: JSON.stringify(process.env.REEFER_REST_HOST),
         }
    
   })]
}
