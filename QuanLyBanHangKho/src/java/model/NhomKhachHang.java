package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class NhomKhachHang {

    private Long id;
    private String ma;
    private String ten;
    private boolean trangthai = true;

    public NhomKhachHang() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMa() {
        return ma;
    }

    public void setMa(String ma) {
        this.ma = ma;
    }

    public String getTen() {
        return ten;
    }

    public void setTen(String ten) {
        this.ten = ten;
    }

    public boolean isTrangthai() {
        return trangthai;
    }

    public void setTrangthai(boolean trangthai) {
        this.trangthai = trangthai;
    }
}
