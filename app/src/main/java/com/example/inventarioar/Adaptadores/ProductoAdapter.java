package com.example.inventarioar.Adaptadores;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.inventarioar.R;
import com.example.inventarioar.models.Producto;

import java.util.List;

public class ProductoAdapter extends RecyclerView.Adapter<ProductoAdapter.ProductoVH> {
    private List<Producto> listaProductos;

    public ProductoAdapter(List<Producto> listaProductos) {
        this.listaProductos = listaProductos;
    }

    @NonNull
    @Override
    public ProductoAdapter.ProductoVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_producto, parent, false);
        return new ProductoVH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductoAdapter.ProductoVH holder, int position) {
        Producto producto = listaProductos.get(position);

        holder.tvNombre.setText(producto.getNombre());
        holder.tvCategoria.setText(producto.getCategoria());
        holder.tvPrecio.setText(String.format("$%.2f", producto.getPrecio()));
        holder.tvStock.setText("Stock: " + producto.getStock());

    }

    @Override
    public int getItemCount() {
        return listaProductos.size();
    }

    public class ProductoVH extends RecyclerView.ViewHolder {
        TextView tvNombre, tvCategoria, tvPrecio, tvStock;
        public ProductoVH(@NonNull View itemView) {
            super(itemView);
            tvNombre = itemView.findViewById(R.id.tvItemNombre);
            tvCategoria = itemView.findViewById(R.id.tvItemCategoria);
            tvPrecio = itemView.findViewById(R.id.tvItemPrecio);
            tvStock = itemView.findViewById(R.id.tvItemStock);
        }
    }
}
