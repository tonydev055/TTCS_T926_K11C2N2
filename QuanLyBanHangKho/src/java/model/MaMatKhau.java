package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class MaMatKhau {

    private Long id;
    private String token;
    private NguoiDung nguoidung;
    private LocalDateTime hethan;
    private boolean dadung = false;

    public MaMatKhau() {}

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

    public boolean isDadung() {
        return dadung;
    }

    public void setDadung(boolean dadung) {
        this.dadung = dadung;
    }
}
