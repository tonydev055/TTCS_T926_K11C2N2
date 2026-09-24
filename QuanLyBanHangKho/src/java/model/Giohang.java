package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class Giohang {
    private Long id;
    private Khachhang khachhang;
    private LocalDateTime thoigiancapnhat = LocalDateTime.now();
    public Giohang() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Khachhang getKhachhang() { return khachhang; }
    public void setKhachhang(Khachhang khachhang) { this.khachhang = khachhang; }
    public LocalDateTime getThoigiancapnhat() { return thoigiancapnhat; }
    public void setThoigiancapnhat(LocalDateTime thoigiancapnhat) { this.thoigiancapnhat = thoigiancapnhat; }
}
