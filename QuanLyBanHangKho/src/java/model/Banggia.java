package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class Banggia {
    private Long id;
    private String mabanggia;
    private String tenbanggia;
    private String nhomkhachhang;
    private LocalDate ngaybatdau;
    private LocalDate ngayketthuc;
    private boolean trangthai = true;
    private int phienban = 1;
    private LocalDateTime thoigiantao = LocalDateTime.now();
    public Banggia() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getMabanggia() { return mabanggia; }
    public void setMabanggia(String mabanggia) { this.mabanggia = mabanggia; }
    public String getTenbanggia() { return tenbanggia; }
    public void setTenbanggia(String tenbanggia) { this.tenbanggia = tenbanggia; }
    public String getNhomkhachhang() { return nhomkhachhang; }
    public void setNhomkhachhang(String nhomkhachhang) { this.nhomkhachhang = nhomkhachhang; }
    public LocalDate getNgaybatdau() { return ngaybatdau; }
    public void setNgaybatdau(LocalDate ngaybatdau) { this.ngaybatdau = ngaybatdau; }
    public LocalDate getNgayketthuc() { return ngayketthuc; }
    public void setNgayketthuc(LocalDate ngayketthuc) { this.ngayketthuc = ngayketthuc; }
    public boolean isTrangthai() { return trangthai; }
    public void setTrangthai(boolean trangthai) { this.trangthai = trangthai; }
    public int getPhienban() { return phienban; }
    public void setPhienban(int phienban) { this.phienban = phienban; }
    public LocalDateTime getThoigiantao() { return thoigiantao; }
    public void setThoigiantao(LocalDateTime thoigiantao) { this.thoigiantao = thoigiantao; }
}
