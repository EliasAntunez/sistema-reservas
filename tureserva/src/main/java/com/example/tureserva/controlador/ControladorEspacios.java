package com.example.tureserva.controlador;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin-complejo/espacios")
public class ControladorEspacios {

    @GetMapping("/listar/{idComplejo}")
    public String listar(@PathVariable("idComplejo") Long idComplejo, Model model) {
        model.addAttribute("idComplejo", idComplejo);
        return "admin-complejo/espacios/listar";
    }
}
