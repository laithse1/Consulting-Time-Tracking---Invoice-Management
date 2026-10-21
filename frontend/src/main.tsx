import React from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import { applicationConfig } from "./config/application";
document.title = applicationConfig.title;
import "./style.css";
createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
