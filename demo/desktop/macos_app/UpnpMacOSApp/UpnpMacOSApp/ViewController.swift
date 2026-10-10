// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates
// SPDX-License-Identifier: BSD-3-Clause

import Cocoa
import Combine

class ViewController: NSViewController {
    
    // 设备列表
    private var deviceTableView: NSTableView!
    private var scrollView: NSScrollView!
    
    // 控制按钮
    private var searchButton: NSButton!
    private var refreshButton: NSButton!
    private var statusLabel: NSTextField!
    
    private let upnpManager = UpnpManagerSwift.shared
    private var cancellables = Set<AnyCancellable>()
    
    override func loadView() {
        // 创建主视图
        self.view = NSView(frame: NSRect(x: 0, y: 0, width: 800, height: 600))
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        
        setupUI()
        
        // 初始化 UPnP 管理器
        if upnpManager.initialize() {
            statusLabel.stringValue = "UPnP initialized. Ready to search!"
        } else {
            statusLabel.stringValue = "Failed to initialize UPnP"
        }
        
        // 监听设备列表变化
        upnpManager.$discoveredDevices
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                self?.deviceTableView.reloadData()
                self?.updateStatusLabel()
            }
            .store(in: &cancellables)
    }
    
    deinit {
        upnpManager.cleanup()
    }

    private func setupUI() {
        // 创建状态栏标签
        statusLabel = NSTextField(labelWithString: "Initializing UPnP...")
        statusLabel.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(statusLabel)
        
        // 创建搜索按钮
        searchButton = NSButton(title: "Search Devices", target: self, action: #selector(searchDevices))
        searchButton.translatesAutoresizingMaskIntoConstraints = false
        searchButton.bezelStyle = .rounded
        view.addSubview(searchButton)
        
        // 创建刷新按钮
        refreshButton = NSButton(title: "Refresh", target: self, action: #selector(refreshDevices))
        refreshButton.translatesAutoresizingMaskIntoConstraints = false
        refreshButton.bezelStyle = .rounded
        view.addSubview(refreshButton)
        
        // 创建表格视图
        scrollView = NSScrollView()
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.hasVerticalScroller = true
        scrollView.hasHorizontalScroller = true
        view.addSubview(scrollView)
        
        deviceTableView = NSTableView()
        deviceTableView.translatesAutoresizingMaskIntoConstraints = false
        deviceTableView.delegate = self
        deviceTableView.dataSource = self
        deviceTableView.allowsMultipleSelection = true
        scrollView.documentView = deviceTableView
        
        // 添加列
        let column1 = NSTableColumn(identifier: NSUserInterfaceItemIdentifier("friendlyName"))
        column1.title = "Friendly Name"
        column1.width = 200
        deviceTableView.addTableColumn(column1)
        
        let column2 = NSTableColumn(identifier: NSUserInterfaceItemIdentifier("deviceType"))
        column2.title = "Device Type"
        column2.width = 200
        deviceTableView.addTableColumn(column2)
        
        let column3 = NSTableColumn(identifier: NSUserInterfaceItemIdentifier("location"))
        column3.title = "Location"
        column3.width = 300
        deviceTableView.addTableColumn(column3)
        
        // 设置约束
        NSLayoutConstraint.activate([
            statusLabel.topAnchor.constraint(equalTo: view.topAnchor, constant: 20),
            statusLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            statusLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            
            searchButton.topAnchor.constraint(equalTo: statusLabel.bottomAnchor, constant: 20),
            searchButton.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            
            refreshButton.topAnchor.constraint(equalTo: statusLabel.bottomAnchor, constant: 20),
            refreshButton.leadingAnchor.constraint(equalTo: searchButton.trailingAnchor, constant: 10),
            
            scrollView.topAnchor.constraint(equalTo: searchButton.bottomAnchor, constant: 20),
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor, constant: -20)
        ])
    }
    
    @objc private func searchDevices() {
        statusLabel.stringValue = "Searching for UPnP devices..."
        upnpManager.startSearch()
    }
    
    @objc private func refreshDevices() {
        upnpManager.refresh()
    }
    
    private func updateStatusLabel() {
        let count = upnpManager.discoveredDevices.count
        if count == 0 {
            statusLabel.stringValue = "Searching for devices..."
        } else {
            statusLabel.stringValue = "Found \(count) device(s)"
        }
    }
}

// MARK: - NSTableViewDataSource
extension ViewController: NSTableViewDataSource {
    
    func numberOfRows(in tableView: NSTableView) -> Int {
        return upnpManager.discoveredDevices.count
    }
}

// MARK: - NSTableViewDelegate
extension ViewController: NSTableViewDelegate {
    
    func tableView(_ tableView: NSTableView, viewFor tableColumn: NSTableColumn?, row: Int) -> NSView? {
        let cell = NSTextField(labelWithString: "")
        cell.isEditable = false
        cell.isSelectable = true
        
        let device = upnpManager.discoveredDevices[row]
        
        if tableColumn?.identifier == NSUserInterfaceItemIdentifier("friendlyName") {
            cell.stringValue = device.friendlyName
        } else if tableColumn?.identifier == NSUserInterfaceItemIdentifier("deviceType") {
            cell.stringValue = device.deviceType
        } else if tableColumn?.identifier == NSUserInterfaceItemIdentifier("location") {
            cell.stringValue = device.location
        }
        
        return cell
    }
}
