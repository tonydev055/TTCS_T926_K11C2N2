package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class Khachhang {
    private Long id;
    private String makhachhang;
    private String tenkhachhang;
    private String sodienthoai;
    private String email;
    private String diachi;
    private String nhomkhachhang = "khachthuong";
    private BigDecimal hanmuccongno = BigDecimal.ZERO;
    private int songayduocno = 0;
    private BigDecimal congnohientai = BigDecimal.ZERO;
    private String diaban;
    private Nguoidung nhanvienphutrach;
    private boolean trangthai = true;
    private Nguoidung nguoidung;
    public Khachhang() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getMakhachhang() { return makhachhang; }
    public void setMakhachhang(String makhachhang) { this.makhachhang = makhachhang; }
    public String getTenkhachhang() { return tenkhachhang; }
    public void setTenkhachhang(String tenkhachhang) { this.tenkhachhang = tenkhachhang; }
    public String getSodienthoai() { return sodienthoai; }
    public void setSodienthoai(String sodienthoai) { this.sodienthoai = sodienthoai; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getDiachi() { return diachi; }
    public void setDiachi(String diachi) { this.diachi = diachi; }
    public String getNhomkhachhang() { return nhomkhachhang; }
    public void setNhomkhachhang(String nhomkhachhang) { this.nhomkhachhang = nhomkhachhang; }
    public BigDecimal getHanmuccongno() { return hanmuccongno; }
    public void setHanmuccongno(BigDecimal hanmuccongno) { this.hanmuccongno = hanmuccongno; }
    public int getSongayduocno() { return songayduocno; }
    public void setSongayduocno(int songayduocno) { this.songayduocno = songayduocno; }
    public BigDecimal getCongnohientai() { return congnohientai; }
    public void setCongnohientai(BigDecimal congnohientai) { this.congnohientai = congnohientai; }
    public String getDiaban() { return diaban; }
    public void setDiaban(String diaban) { this.diaban = diaban; }
    public Nguoidung getNhanvienphutrach() { return nhanvienphutrach; }
    public void setNhanvienphutrach(Nguoidung nhanvienphutrach) { this.nhanvienphutrach = nhanvienphutrach; }
    public boolean isTrangthai() { return trangthai; }
    public void setTrangthai(boolean trangthai) { this.trangthai = trangthai; }
    public Nguoidung getNguoidung() { return nguoidung; }
    public void setNguoidung(Nguoidung nguoidung) { this.nguoidung = nguoidung; }
}
