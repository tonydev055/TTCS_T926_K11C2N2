package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class MaLamMoi {

    private Long id;
    private String token;
    private NguoiDung nguoidung;
    private LocalDateTime hethan;
    private boolean dathuhoi = false;

    public MaLamMoi() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public NguoiDung getNguoidung() {
        return nguoidung;
    }

    public void setNguoidung(NguoiDung nguoidung) {
        this.nguoidung = nguoidung;
    }

    public LocalDateTime getHethan() {
        return hethan;
    }

    public void setHethan(LocalDateTime hethan) {
        this.hethan = hethan;
    }

    public boolean isDathuhoi() {
        return dathuhoi;
    }

    public void setDathuhoi(boolean dathuhoi) {
        this.dathuhoi = dathuhoi;
    }
}
