package com.fooddelivery.orderservice.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    private String id;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "menu_item_id", nullable = false)
    private String menuItemId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price_cents", nullable = false)
    private int unitPriceCents;

    protected OrderItem() {}

    public OrderItem(String id, String orderId, String menuItemId, int quantity, int unitPriceCents) {
        this.id = id;
        this.orderId = orderId;
        this.menuItemId = menuItemId;
        this.quantity = quantity;
        this.unitPriceCents = unitPriceCents;
    }

    public String getId()            { return id; }
    public String getOrderId()       { return orderId; }
    public String getMenuItemId()    { return menuItemId; }
    public int    getQuantity()      { return quantity; }
    public int    getUnitPriceCents(){ return unitPriceCents; }
}
