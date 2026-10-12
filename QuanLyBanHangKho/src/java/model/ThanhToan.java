package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class ThanhToan {

    private Long id;
    private String maphieuthu;
    private HoaDon hoadon;
    private KhachHang khachhang;
    private BigDecimal sotien;
    private String phuongthuc;
    private LocalDateTime thoigian = LocalDateTime.now();
    private String thamchieu;
    private String ghichu;

    public ThanhToan() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMaphieuthu() {
        return maphieuthu;
    }

    public void setMaphieuthu(String maphieuthu) {
        this.maphieuthu = maphieuthu;
    }

    public HoaDon getHoadon() {
        return hoadon;
    }

    public void setHoadon(HoaDon hoadon) {
        this.hoadon = hoadon;
    }

    public KhachHang getKhachhang() {
        return khachhang;
    }

    public void setKhachhang(KhachHang khachhang) {
        this.khachhang = khachhang;
    }

    public BigDecimal getSotien() {
        return sotien;
    }

    public void setSotien(BigDecimal sotien) {
        this.sotien = sotien;
    }

    public String getPhuongthuc() {
        return phuongthuc;
    }

    public void setPhuongthuc(String phuongthuc) {
        this.phuongthuc = phuongthuc;
    }

    public LocalDateTime getThoigian() {
        return thoigian;
    }

    public void setThoigian(LocalDateTime thoigian) {
        this.thoigian = thoigian;
    }

    public String getThamchieu() {
        return thamchieu;
    }

    public void setThamchieu(String thamchieu) {
        this.thamchieu = thamchieu;
    }

    public String getGhichu() {
        return ghichu;
    }

    public void setGhichu(String ghichu) {
        this.ghichu = ghichu;
    }
}
