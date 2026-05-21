package com.example.inventarioar.Adaptadores;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.inventarioar.R;
import com.example.inventarioar.models.Producto;

import java.util.List;

public class ProductoHomeAdapter extends RecyclerView.Adapter<ProductoHomeAdapter.ProductoHomeVH> {
    private final List<Producto> productos;
    private final String sucursalKey;

    public ProductoHomeAdapter(List<Producto> productos, String sucursalKey) {
        this.productos = productos;
        this.sucursalKey = sucursalKey;
    }

    @NonNull
    @Override
    public ProductoHomeAdapter.ProductoHomeVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_producto_home, parent, false);
        return new ProductoHomeVH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductoHomeAdapter.ProductoHomeVH holder, int position) {
        Producto p = productos.get(position);
        int stock = 0;
        if (p.getStockPorSucursal() != null && p.getStockPorSucursal().containsKey(sucursalKey)){
            stock = p.getStockPorSucursal().get(sucursalKey);
        }
        holder.tvNombre.setText(p.getNombre());
        holder.tvCategoria.setText(p.getCategoria());
        holder.tvPrecio.setText(String.format("$%.2f", p.getPrecio()));
        holder.tvStock.setText("Stock: " + stock);

        Glide.with(holder.itemView.getContext())
                .load(p.getImagenUrl())
                .placeholder(R.drawable.ic_inventory)
                .error(R.drawable.ic_inventory)
                .into(holder.imgProducto);
    }

    @Override
    public int getItemCount() {
        return productos.size();
    }

    public class ProductoHomeVH extends RecyclerView.ViewHolder {
        TextView tvNombre, tvCategoria, tvPrecio, tvStock;
        ImageView imgProducto;
        public ProductoHomeVH(@NonNull View itemView) {
            super(itemView);
            tvNombre = itemView.findViewById(R.id.tvNombreHome);
            tvCategoria = itemView.findViewById(R.id.tvCategoriaHome);
            tvPrecio = itemView.findViewById(R.id.tvPrecioHome);
            tvStock = itemView.findViewById(R.id.tvStockHome);
            imgProducto = itemView.findViewById(R.id.imgProductoHome);
        }
    }
}
