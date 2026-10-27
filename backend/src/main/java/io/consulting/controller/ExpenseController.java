package io.consulting.controller;

import io.consulting.service.ExpenseService;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseService expenses;

    public ExpenseController(ExpenseService expenses) {
        this.expenses = expenses;
    }

    @PostMapping
    public Object saveExpense(@RequestBody Map<String, Object> request) {
        return expenses.expense(request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable int id, @RequestParam boolean confirmed) {
        expenses.delete(id, confirmed);
    }
}
