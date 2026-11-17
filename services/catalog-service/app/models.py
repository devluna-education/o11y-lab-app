from pydantic import BaseModel, Field


class Product(BaseModel):
    id: int
    name: str
    category: str
    price: float
    stock: int


class ProductPage(BaseModel):
    total: int
    items: list[Product]


class ReserveRequest(BaseModel):
    quantity: int = Field(ge=1, le=100)


class ReserveResponse(BaseModel):
    product_id: int
    reserved: int
    remaining_stock: int
