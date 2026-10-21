package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class ChiTietPhieuXuat {

    private Long id;
    private PhieuXuat phieuxuat;
    private SanPham sanpham;
    private LoHang lohang;
    private int soluong;

    public ChiTietPhieuXuat() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public PhieuXuat getPhieuxuat() {
        return phieuxuat;
    }

    public void setPhieuxuat(PhieuXuat phieuxuat) {
        this.phieuxuat = phieuxuat;
    }

    public SanPham getSanpham() {
        return sanpham;
    }

    public void setSanpham(SanPham sanpham) {
        this.sanpham = sanpham;
    }

    public LoHang getLohang() {
        return lohang;
    }

    public void setLohang(LoHang lohang) {
        this.lohang = lohang;
    }

    public int getSoluong() {
        return soluong;
    }

    public void setSoluong(int soluong) {
        this.soluong = soluong;
    }
}
