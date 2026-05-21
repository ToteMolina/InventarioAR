package com.example.inventarioar.Adaptadores;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.inventarioar.R;
import com.example.inventarioar.models.Producto;

import java.util.List;

public class ProductoAdapter extends RecyclerView.Adapter<ProductoAdapter.ProductoVH> {
    private List<Producto> listaProductos;
    private OnProductoListener listener;
    private String sucursalFiltroActual = "Almacén";
    public interface OnProductoListener{
        void onEditar(Producto producto);
        void onEliminar(Producto producto);
    }

    public ProductoAdapter(List<Producto> listaProductos, OnProductoListener listener) {
        this.listaProductos = listaProductos;
        this.listener = listener;
    }

    public void setSucursalFiltroActual(String sucursalKey){
        this.sucursalFiltroActual = sucursalKey;
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
        if (sucursalFiltroActual.equals("Almacén") || sucursalFiltroActual.isEmpty()){
            holder.tvStock.setText("Stock Total: " + producto.getStock());
        } else {
            int stockEspecifico = 0;
            if (producto.getStockPorSucursal() != null && producto.getStockPorSucursal().containsKey(sucursalFiltroActual)){
                stockEspecifico = producto.getStockPorSucursal().get(sucursalFiltroActual);
            }
            holder.tvStock.setText("Stock: " + stockEspecifico);
        }

        Glide.with(holder.itemView.getContext())
                        .load(producto.getImagenUrl())
                        .placeholder(R.drawable.ic_inventory)
                        .error(R.drawable.ic_inventory)
                        .into(holder.imgProducto);

        holder.itemView.setOnLongClickListener(v->{
            new AlertDialog.Builder(v.getContext())
                    .setTitle(producto.getNombre())
                    .setItems(new String[]{"Editar", "Eliminar"}, (dialog, which)->{
                        if (which == 0) listener.onEditar(producto);
                        else listener.onEliminar(producto);
                    })
                    .show();
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return listaProductos.size();
    }

    public class ProductoVH extends RecyclerView.ViewHolder {
        TextView tvNombre, tvCategoria, tvPrecio, tvStock;
        ImageView imgProducto;
        public ProductoVH(@NonNull View itemView) {
            super(itemView);
            tvNombre = itemView.findViewById(R.id.tvItemNombre);
            tvCategoria = itemView.findViewById(R.id.tvItemCategoria);
            tvPrecio = itemView.findViewById(R.id.tvItemPrecio);
            tvStock = itemView.findViewById(R.id.tvItemStock);
            imgProducto = itemView.findViewById(R.id.imgProductoItem);
        }
    }
}
