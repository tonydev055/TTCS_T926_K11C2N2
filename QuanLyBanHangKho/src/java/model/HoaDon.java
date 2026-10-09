package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class HoaDon {

    private Long id;
    private String mahoadon;
    private DonHang donhang;
    private LocalDateTime thoigian = LocalDateTime.now();
    private LocalDate hanthanhtoan;
    private BigDecimal tongtien = BigDecimal.ZERO;
    private BigDecimal giamgia = BigDecimal.ZERO;
    private BigDecimal thanhtien = BigDecimal.ZERO;
    private BigDecimal dathanhtoan = BigDecimal.ZERO;
    private String trangthai;

    public HoaDon() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMahoadon() {
        return mahoadon;
    }

    public void setMahoadon(String mahoadon) {
        this.mahoadon = mahoadon;
    }

    public DonHang getDonhang() {
        return donhang;
    }

    public void setDonhang(DonHang donhang) {
        this.donhang = donhang;
    }

    public LocalDateTime getThoigian() {
        return thoigian;
    }

    public void setThoigian(LocalDateTime thoigian) {
        this.thoigian = thoigian;
    }

    public LocalDate getHanthanhtoan() {
        return hanthanhtoan;
    }

    public void setHanthanhtoan(LocalDate hanthanhtoan) {
        this.hanthanhtoan = hanthanhtoan;
    }

    public BigDecimal getTongtien() {
        return tongtien;
    }

    public void setTongtien(BigDecimal tongtien) {
        this.tongtien = tongtien;
    }

    public BigDecimal getGiamgia() {
        return giamgia;
    }

    public void setGiamgia(BigDecimal giamgia) {
        this.giamgia = giamgia;
    }

    public BigDecimal getThanhtien() {
        return thanhtien;
    }

    public void setThanhtien(BigDecimal thanhtien) {
        this.thanhtien = thanhtien;
    }

    public BigDecimal getDathanhtoan() {
        return dathanhtoan;
    }

    public void setDathanhtoan(BigDecimal dathanhtoan) {
        this.dathanhtoan = dathanhtoan;
    }

    public String getTrangthai() {
        return trangthai;
    }

    public void setTrangthai(String trangthai) {
        this.trangthai = trangthai;
    }
}
