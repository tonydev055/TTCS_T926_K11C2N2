package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class ChiTietChuyenKho {

    private Long id;
    private ChuyenKho chuyenkho;
    private SanPham sanpham;
    private int soluong;

    public ChiTietChuyenKho() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ChuyenKho getChuyenkho() {
        return chuyenkho;
    }

    public void setChuyenkho(ChuyenKho chuyenkho) {
        this.chuyenkho = chuyenkho;
    }

    public SanPham getSanpham() {
        return sanpham;
    }

    public void setSanpham(SanPham sanpham) {
        this.sanpham = sanpham;
    }

    public int getSoluong() {
        return soluong;
    }

    public void setSoluong(int soluong) {
        this.soluong = soluong;
    }
}
