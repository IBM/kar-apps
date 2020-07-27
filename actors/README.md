# Reefer Actor Service

To launch Reefer Actor service run:

kar --app reefer -service kcontainer -actors order,reefer,voyage,reefer-provisioner mvn liberty:run

To test placing an order run the following in a separate shell:

kar -runtime_port 32123 -app reefer curl -s -H "Content-Type: application/kar+json" -X POST http://localhost:32123/kar/v1/actor/order/dummyid/call/createOrder -d '[{"order":{"number":10,"orderId":"4519","orderVoyageId":"144312","orderProductQty":2400}}]'
