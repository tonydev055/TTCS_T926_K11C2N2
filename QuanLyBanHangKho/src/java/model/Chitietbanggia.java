package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class Chitietbanggia {
    private Long id;
    private Banggia banggia;
    private Sanpham sanpham;
    private BigDecimal giaban;
    private BigDecimal giasan;
    public Chitietbanggia() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Banggia getBanggia() { return banggia; }
    public void setBanggia(Banggia banggia) { this.banggia = banggia; }
    public Sanpham getSanpham() { return sanpham; }
    public void setSanpham(Sanpham sanpham) { this.sanpham = sanpham; }
    public BigDecimal getGiaban() { return giaban; }
    public void setGiaban(BigDecimal giaban) { this.giaban = giaban; }
    public BigDecimal getGiasan() { return giasan; }
    public void setGiasan(BigDecimal giasan) { this.giasan = giasan; }
}
