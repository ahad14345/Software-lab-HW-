const state = {
    credentials: sessionStorage.getItem("umsCredentials"),
    username: sessionStorage.getItem("umsUsername"),
    role: null,
    resources: [],
    resource: null,
    editingId: null
};

const $ = (id) => document.getElementById(id);
const resourceLabels = { admins: "Admins", teachers: "Teachers", students: "Students" };

function authHeaders() {
    return { "Content-Type": "application/json", Authorization: `Basic ${state.credentials}` };
}

async function request(path, options = {}) {
    const response = await fetch(path, { ...options, headers: { ...authHeaders(), ...(options.headers || {}) } });
    if (response.status === 401) throw new Error("Invalid username or password.");
    if (response.status === 403) throw new Error("Forbidden: your role cannot access this resource.");
    if (!response.ok) {
        let detail = `Request failed (${response.status})`;
        try { detail = (await response.json()).detail || (await response.text()) || detail; } catch (_) {}
        throw new Error(detail);
    }
    return response.status === 204 ? null : response.json();
}

async function detectPermissions() {
    const candidates = ["admins", "teachers", "students"];
    const permitted = [];
    for (const resource of candidates) {
        try {
            await request(`/api/${resource}`);
            permitted.push(resource);
        } catch (error) {
            if (error.message.startsWith("Invalid")) throw error;
        }
    }
    if (!permitted.length) throw new Error("This account has no permitted resources.");
    state.resources = permitted;
    state.role = permitted.includes("admins") ? "ADMIN" : permitted[0].slice(0, -1).toUpperCase();
}

function showApp() {
    $("login-view").classList.add("hidden");
    $("app-view").classList.remove("hidden");
    $("session-user").textContent = state.username;
    $("session-role").textContent = state.role;
    $("tabs").innerHTML = state.resources.map((resource, index) =>
        `<button class="tab ${index === 0 ? "active" : ""}" data-resource="${resource}">${resourceLabels[resource]}</button>`).join("");
    document.querySelectorAll(".tab").forEach((tab) => tab.addEventListener("click", () => selectResource(tab.dataset.resource)));
    selectResource(state.resources[0]);
}

async function selectResource(resource) {
    state.resource = resource;
    state.editingId = null;
    document.querySelectorAll(".tab").forEach((tab) => tab.classList.toggle("active", tab.dataset.resource === resource));
    $("resource-title").textContent = resourceLabels[resource];
    $("role-field").classList.toggle("hidden", state.role !== "ADMIN");
    resetForm();
    await loadUsers();
}

async function loadUsers() {
    try {
        const users = await request(`/api/${state.resource}`);
        $("table-container").innerHTML = users.length ? `
            <div class="table-wrap"><table><thead><tr><th>ID</th><th>Username</th><th>Role</th><th>Full name</th><th>Email</th><th>Phone</th><th>Department</th><th>Address</th><th>Actions</th></tr></thead>
            <tbody>${users.map((user) => `<tr><td>${user.id}</td><td>${escapeHtml(user.username)}</td><td>${user.role}</td>
            <td>${escapeHtml(user.fullName || "-")}</td><td>${escapeHtml(user.email || "-")}</td><td>${escapeHtml(user.phone || "-")}</td><td>${escapeHtml(user.department || "-")}</td><td>${escapeHtml(user.address || "-")}</td>
            <td class="actions"><button data-edit="${user.id}">Edit</button>${state.role === "ADMIN" ? `<button class="delete-button" data-delete="${user.id}">Delete</button>` : ""}</td></tr>`).join("")}</tbody></table></div>` :
            `<p class="empty">No ${resourceLabels[state.resource].toLowerCase()} found.</p>`;
        document.querySelectorAll("[data-edit]").forEach((button) => button.addEventListener("click", () => editUser(button.dataset.edit)));
        document.querySelectorAll("[data-delete]").forEach((button) => button.addEventListener("click", () => deleteUser(button.dataset.delete)));
        if (state.role !== "ADMIN" && users.length) editUser(users[0].id);
    } catch (error) { showNotice(error.message, true); }
}

function editUser(id) {
    state.editingId = id;
    const row = [...document.querySelectorAll("[data-edit]")].find((button) => button.dataset.edit === id).closest("tr");
    $("user-id").value = id;
    $("form-username").value = row.children[1].textContent;
    $("form-password").value = "";
    $("form-role").value = row.children[2].textContent;
    $("form-fullName").value = row.children[3].textContent === "-" ? "" : row.children[3].textContent;
    $("form-email").value = row.children[4].textContent === "-" ? "" : row.children[4].textContent;
    $("form-phone").value = row.children[5].textContent === "-" ? "" : row.children[5].textContent;
    $("form-department").value = row.children[6].textContent === "-" ? "" : row.children[6].textContent;
    $("form-address").value = row.children[7].textContent === "-" ? "" : row.children[7].textContent;
    $("form-title").textContent = "Edit user";
    $("save-user").textContent = "Save changes";
    $("cancel-edit").classList.remove("hidden");
    $("form-password").focus();
}

async function deleteUser(id) {
    if (!confirm("Delete this user?")) return;
    try {
        await request(`/api/${state.resource}/${id}`, { method: "DELETE" });
        showNotice("User deleted.");
        await loadUsers();
    } catch (error) { showNotice(error.message, true); }
}

function resetForm() {
    state.editingId = null;
    $("user-form").reset();
    $("user-id").value = "";
    $("form-title").textContent = "Create user";
    $("save-user").textContent = "Create user";
    $("cancel-edit").classList.add("hidden");
    $("form-error").textContent = "";
    $("form-fullName").value = "";
    $("form-email").value = "";
    $("form-phone").value = "";
    $("form-department").value = "";
    $("form-address").value = "";
    if (state.resource) $("form-role").value = state.resource === "teachers" ? "TEACHER" : state.resource === "students" ? "STUDENT" : "ADMIN";
}

function showNotice(message, error = false) {
    $("notice").textContent = message;
    $("notice").classList.remove("hidden");
    $("notice").style.color = error ? "#9d2840" : "";
    $("notice").style.background = error ? "#fff0f2" : "";
    setTimeout(() => $("notice").classList.add("hidden"), 4000);
}

function escapeHtml(value) {
    return value.replace(/[&<>"']/g, (character) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#039;" }[character]));
}

$("login-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    $("login-error").textContent = "";
    state.username = $("username").value.trim();
    state.credentials = btoa(`${state.username}:${$("password").value}`);
    try {
        await detectPermissions();
        sessionStorage.setItem("umsCredentials", state.credentials);
        sessionStorage.setItem("umsUsername", state.username);
        showApp();
    } catch (error) {
        $("login-error").textContent = error.message;
        state.credentials = null;
    }
});

$("user-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    $("form-error").textContent = "";
    const payload = {
        username: $("form-username").value.trim(),
        password: $("form-password").value,
        role: $("form-role").value,
        fullName: $("form-fullName").value.trim(),
        email: $("form-email").value.trim(),
        phone: $("form-phone").value.trim(),
        department: $("form-department").value.trim(),
        address: $("form-address").value.trim()
    };
    if (state.editingId && !payload.password) delete payload.password;
    try {
        await request(`/api/${state.resource}${state.editingId ? `/${state.editingId}` : ""}`, {
            method: state.editingId ? "PUT" : "POST", body: JSON.stringify(payload)
        });
        showNotice(state.editingId ? "User updated." : "User created.");
        resetForm();
        await loadUsers();
    } catch (error) { $("form-error").textContent = error.message; }
});

$("cancel-edit").addEventListener("click", resetForm);
$("refresh").addEventListener("click", loadUsers);
$("logout").addEventListener("click", () => {
    sessionStorage.clear();
    window.location.reload();
});

if (state.credentials) {
    detectPermissions().then(showApp).catch(() => sessionStorage.clear());
}
