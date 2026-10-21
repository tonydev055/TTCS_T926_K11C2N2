package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class ChietKhau {

    private Long id;
    private String nhomkhachhang;
    private SanPham sanpham;
    private NhomHang nhomhang;
    private int soluongtu = 1;
    private BigDecimal phantram;
    private boolean trangthai = true;

    public ChietKhau() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNhomkhachhang() {
        return nhomkhachhang;
    }

    public void setNhomkhachhang(String nhomkhachhang) {
        this.nhomkhachhang = nhomkhachhang;
    }

    public SanPham getSanpham() {
        return sanpham;
    }

    public void setSanpham(SanPham sanpham) {
        this.sanpham = sanpham;
    }

    public NhomHang getNhomhang() {
        return nhomhang;
    }

    public void setNhomhang(NhomHang nhomhang) {
        this.nhomhang = nhomhang;
    }

    public int getSoluongtu() {
        return soluongtu;
    }

    public void setSoluongtu(int soluongtu) {
        this.soluongtu = soluongtu;
    }

    public BigDecimal getPhantram() {
        return phantram;
    }

    public void setPhantram(BigDecimal phantram) {
        this.phantram = phantram;
    }

    public boolean isTrangthai() {
        return trangthai;
    }

    public void setTrangthai(boolean trangthai) {
        this.trangthai = trangthai;
    }
}
