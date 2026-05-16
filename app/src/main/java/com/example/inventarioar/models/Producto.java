package com.example.inventarioar.models;

public class Producto {
    private String id;
    private String nombre;
    private String categoria;
    private String descripcion;
    private double precio;
    private int stock;

    private String imagenUrl;
    private String modelo3DUrl;

    public Producto() {
    }

    public Producto(String id, String nombre, String categoria, String descripcion, double precio, int stock) {
        this.id = id;
        this.nombre = nombre;
        this.categoria = categoria;
        this.descripcion = descripcion;
        this.precio = precio;
        this.stock = stock;
        this.imagenUrl = "";
        this.modelo3DUrl = "";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public double getPrecio() {
        return precio;
    }

    public void setPrecio(double precio) {
        this.precio = precio;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public String getImagenUrl() {
        return imagenUrl;
    }

    public void setImagenUrl(String imagenUrl) {
        this.imagenUrl = imagenUrl;
    }

    public String getModelo3DUrl() {
        return modelo3DUrl;
    }

    public void setModelo3DUrl(String modelo3DUrl) {
        this.modelo3DUrl = modelo3DUrl;
    }
}
