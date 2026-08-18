package com.leaseguard.controller;

import com.leaseguard.dto.ImportPreviewResult;
import com.leaseguard.service.ImportService;
import com.leaseguard.exception.ImportValidationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/import")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @GetMapping
    public String form() {
        return "import/form";
    }

    // Handle the submission of the import form and display a preview of the uploaded CSV file.
    @PostMapping("/preview")
    public String preview(@RequestParam("file") MultipartFile file, Model model) throws IOException {
        if (file.isEmpty()) {
            model.addAttribute("uploadError", "Please choose a CSV file to upload.");
            return "import/form";
        }
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        ImportPreviewResult result = importService.previewUpload(file.getOriginalFilename(), content);
        model.addAttribute("preview", result);
        return "import/preview";
    }

    // Display a preview of the bundled demo CSV data.
    @PostMapping("/preview-demo")
    public String previewBundledDemoData(Model model) {
        model.addAttribute("preview", importService.previewBundledDemoData());
        return "import/preview";
    }

    // Commit the uploaded CSV file, saving its data and redirecting to the dashboard with a success message.
    @PostMapping("/commit")
    public String commit(@RequestParam String filename, @RequestParam String content,
                          RedirectAttributes redirectAttributes) {
        var result = importService.commit(filename, content);
        redirectAttributes.addFlashAttribute("importSuccess", result);
        return "redirect:/dashboard";
    }

    // Handle import validation failures by displaying the preview with errors and an appropriate message.
    @ExceptionHandler(ImportValidationException.class)
    public String handleRevalidationFailure(ImportValidationException e, Model model) {
        model.addAttribute("preview", e.result());
        model.addAttribute("uploadError", "The file changed since it was previewed. Please review the errors below.");
        return "import/preview";
    }
}
