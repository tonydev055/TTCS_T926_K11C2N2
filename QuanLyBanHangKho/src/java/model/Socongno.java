package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class Socongno {
    private Long id;
    private Khachhang khachhang;
    private String loaigiaodich;
    private String mathamchieu;
    private BigDecimal phatsinhno = BigDecimal.ZERO;
    private BigDecimal phatsinhco = BigDecimal.ZERO;
    private BigDecimal sodu = BigDecimal.ZERO;
    private LocalDateTime thoigian = LocalDateTime.now();
    private String diengiai;
    public Socongno() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Khachhang getKhachhang() { return khachhang; }
    public void setKhachhang(Khachhang khachhang) { this.khachhang = khachhang; }
    public String getLoaigiaodich() { return loaigiaodich; }
    public void setLoaigiaodich(String loaigiaodich) { this.loaigiaodich = loaigiaodich; }
    public String getMathamchieu() { return mathamchieu; }
    public void setMathamchieu(String mathamchieu) { this.mathamchieu = mathamchieu; }
    public BigDecimal getPhatsinhno() { return phatsinhno; }
    public void setPhatsinhno(BigDecimal phatsinhno) { this.phatsinhno = phatsinhno; }
    public BigDecimal getPhatsinhco() { return phatsinhco; }
    public void setPhatsinhco(BigDecimal phatsinhco) { this.phatsinhco = phatsinhco; }
    public BigDecimal getSodu() { return sodu; }
    public void setSodu(BigDecimal sodu) { this.sodu = sodu; }
    public LocalDateTime getThoigian() { return thoigian; }
    public void setThoigian(LocalDateTime thoigian) { this.thoigian = thoigian; }
    public String getDiengiai() { return diengiai; }
    public void setDiengiai(String diengiai) { this.diengiai = diengiai; }
}
