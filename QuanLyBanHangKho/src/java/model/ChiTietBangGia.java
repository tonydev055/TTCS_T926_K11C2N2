package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class ChiTietBangGia {

    private Long id;
    private BangGia banggia;
    private SanPham sanpham;
    private BigDecimal giaban;
    private BigDecimal giasan;

    public ChiTietBangGia() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BangGia getBanggia() {
        return banggia;
    }

    public void setBanggia(BangGia banggia) {
        this.banggia = banggia;
    }

    public SanPham getSanpham() {
        return sanpham;
    }

    public void setSanpham(SanPham sanpham) {
        this.sanpham = sanpham;
    }

    public BigDecimal getGiaban() {
        return giaban;
    }

    public void setGiaban(BigDecimal giaban) {
        this.giaban = giaban;
    }

    public BigDecimal getGiasan() {
        return giasan;
    }

    public void setGiasan(BigDecimal giasan) {
        this.giasan = giasan;
    }
}
