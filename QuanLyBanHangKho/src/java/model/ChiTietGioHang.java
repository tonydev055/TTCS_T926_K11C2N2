package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class ChiTietGioHang {

    private Long id;
    private GioHang giohang;
    private SanPham sanpham;
    private int soluong;

    public ChiTietGioHang() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public GioHang getGiohang() {
        return giohang;
    }

    public void setGiohang(GioHang giohang) {
        this.giohang = giohang;
    }

    public SanPham getSanpham() {
        return sanpham;
    }

    public void setSanpham(SanPham sanpham) {
        this.sanpham = sanpham;
    }

    public int getSoluong() {
        return soluong;
    }

    public void setSoluong(int soluong) {
        this.soluong = soluong;
    }
}
