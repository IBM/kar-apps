export interface Order {
  orderId: string,
  product: string,
  productQty: number,
  origin: string,
  destination: string,
  sailDate: string,
  transitTime: number,
  voyageId: string,
  reeferIds: string
}
