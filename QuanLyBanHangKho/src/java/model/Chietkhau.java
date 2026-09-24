package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class Chietkhau {
    private Long id;
    private String nhomkhachhang;
    private Sanpham sanpham;
    private Nhomhang nhomhang;
    private int soluongtu = 1;
    private BigDecimal phantram;
    private boolean trangthai = true;
    public Chietkhau() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNhomkhachhang() { return nhomkhachhang; }
    public void setNhomkhachhang(String nhomkhachhang) { this.nhomkhachhang = nhomkhachhang; }
    public Sanpham getSanpham() { return sanpham; }
    public void setSanpham(Sanpham sanpham) { this.sanpham = sanpham; }
    public Nhomhang getNhomhang() { return nhomhang; }
    public void setNhomhang(Nhomhang nhomhang) { this.nhomhang = nhomhang; }
    public int getSoluongtu() { return soluongtu; }
    public void setSoluongtu(int soluongtu) { this.soluongtu = soluongtu; }
    public BigDecimal getPhantram() { return phantram; }
    public void setPhantram(BigDecimal phantram) { this.phantram = phantram; }
    public boolean isTrangthai() { return trangthai; }
    public void setTrangthai(boolean trangthai) { this.trangthai = trangthai; }
}
