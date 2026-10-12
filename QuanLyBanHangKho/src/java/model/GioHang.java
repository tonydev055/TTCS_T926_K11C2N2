package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class GioHang {

    private Long id;
    private KhachHang khachhang;
    private LocalDateTime thoigiancapnhat = LocalDateTime.now();

    public GioHang() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public KhachHang getKhachhang() {
        return khachhang;
    }

    public void setKhachhang(KhachHang khachhang) {
        this.khachhang = khachhang;
    }

    public LocalDateTime getThoigiancapnhat() {
        return thoigiancapnhat;
    }

    public void setThoigiancapnhat(LocalDateTime thoigiancapnhat) {
        this.thoigiancapnhat = thoigiancapnhat;
    }
}
