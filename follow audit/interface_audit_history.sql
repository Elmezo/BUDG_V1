-- phpMyAdmin SQL Dump
-- version 5.2.1
-- https://www.phpmyadmin.net/
--
-- Host: 127.0.0.1
-- Generation Time: Nov 23, 2025 at 02:14 PM
-- Server version: 10.4.32-MariaDB
-- PHP Version: 8.2.12

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Database: `project`
--

-- --------------------------------------------------------

--
-- Table structure for table `interface_audit_history`
--

CREATE TABLE `interface_audit_history` (
  `id` int(11) NOT NULL,
  `auditidpk` int(11) NOT NULL,
  `object` varchar(255) DEFAULT NULL,
  `event` varchar(255) DEFAULT NULL,
  `updateType` varchar(255) DEFAULT NULL,
  `field` varchar(255) DEFAULT NULL,
  `from` varchar(255) DEFAULT NULL,
  `to` varchar(255) DEFAULT NULL,
  `author` varchar(255) DEFAULT NULL,
  `date` datetime DEFAULT current_timestamp(),
  `lastChange` datetime DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `interface_audit_history`
--

INSERT INTO `interface_audit_history` (`id`, `auditidpk`, `object`, `event`, `updateType`, `field`, `from`, `to`, `author`, `date`, `lastChange`) VALUES
(19, 133, 'Stakeholder', 'status', 'Accepted', 'Role Status', 'Active', 'Yes', 'System', '2025-11-09 15:35:22', '2025-11-09 15:35:22'),
(27, 18, 'Interface', 'Details', 'Added', 'Name', NULL, 'نمتمنز', 'Alice Smith', '2025-10-23 15:25:54', '2025-10-23 15:25:54'),
(27, 19, 'Interface', 'Details', 'Added', 'Reference Number', NULL, 'نمكنمك', 'Alice Smith', '2025-10-23 15:25:54', '2025-10-23 15:25:54'),
(27, 20, 'Interface', 'Details', 'Added', 'Description', NULL, 'gfdhx', 'Alice Smith', '2025-10-23 15:25:54', '2025-10-23 15:25:54'),
(27, 21, 'Interface', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-23 15:25:54', '2025-10-23 15:25:54'),
(27, 22, 'Interface', 'Details', 'Added', 'Lifecycle', NULL, 'Production', 'Alice Smith', '2025-10-23 15:25:54', '2025-10-23 15:25:54'),
(27, 23, 'Interface', 'Details', 'Added', 'Source System', NULL, 'HR System1', 'Alice Smith', '2025-10-23 15:25:54', '2025-10-23 15:25:54'),
(27, 24, 'Interface', 'Details', 'Added', 'Target System', NULL, 'Finance System', 'Alice Smith', '2025-10-23 15:25:54', '2025-10-23 15:25:54'),
(27, 25, 'Interface', 'Details', 'Added', 'Automation', NULL, 'Automated', 'Alice Smith', '2025-10-23 15:25:54', '2025-10-23 15:25:54'),
(27, 26, 'Interface', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-10-23 15:25:54', '2025-10-23 15:25:54'),
(27, 27, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Change Request OwnerR', 'Alice Smith', '2025-10-23 15:25:54', '2025-10-23 15:25:54'),
(27, 28, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-23 15:25:54', '2025-10-23 15:25:54'),
(27, 29, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-23 15:25:54', '2025-10-23 15:25:54'),
(28, 30, 'Interface', 'Details', 'Added', 'Name', NULL, 'jghj', 'Alice Smith', '2025-10-23 16:38:11', '2025-10-23 16:38:11'),
(28, 31, 'Interface', 'Details', 'Added', 'Reference Number', NULL, 'kj', 'Alice Smith', '2025-10-23 16:38:11', '2025-10-23 16:38:11'),
(28, 32, 'Interface', 'Details', 'Added', 'Description', NULL, 'gjkghkh', 'Alice Smith', '2025-10-23 16:38:11', '2025-10-23 16:38:11'),
(28, 33, 'Interface', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-23 16:38:11', '2025-10-23 16:38:11'),
(28, 34, 'Interface', 'Details', 'Added', 'Lifecycle', NULL, 'Production', 'Alice Smith', '2025-10-23 16:38:11', '2025-10-23 16:38:11'),
(28, 35, 'Interface', 'Details', 'Added', 'Source System', NULL, 'Core System1', 'Alice Smith', '2025-10-23 16:38:11', '2025-10-23 16:38:11'),
(28, 36, 'Interface', 'Details', 'Added', 'Target System', NULL, 'HR System1', 'Alice Smith', '2025-10-23 16:38:11', '2025-10-23 16:38:11'),
(28, 37, 'Interface', 'Details', 'Added', 'Automation', NULL, 'Automated', 'Alice Smith', '2025-10-23 16:38:11', '2025-10-23 16:38:11'),
(28, 38, 'Interface', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-10-23 16:38:11', '2025-10-23 16:38:11'),
(28, 39, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Change Request OwnerR', 'Alice Smith', '2025-10-23 16:38:11', '2025-10-23 16:38:11'),
(28, 40, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-23 16:38:11', '2025-10-23 16:38:11'),
(28, 41, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-23 16:38:11', '2025-10-23 16:38:11'),
(29, 42, 'Interface', 'Details', 'Added', 'Name', NULL, 'asd', 'Alice Smith', '2025-10-27 15:18:50', '2025-10-27 15:18:50'),
(29, 43, 'Interface', 'Details', 'Added', 'Reference Number', NULL, 'dsa', 'Alice Smith', '2025-10-27 15:18:50', '2025-10-27 15:18:50'),
(29, 44, 'Interface', 'Details', 'Added', 'Description', NULL, 'asdasda', 'Alice Smith', '2025-10-27 15:18:50', '2025-10-27 15:18:50'),
(29, 45, 'Interface', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-27 15:18:50', '2025-10-27 15:18:50'),
(29, 46, 'Interface', 'Details', 'Added', 'Lifecycle', NULL, 'Production', 'Alice Smith', '2025-10-27 15:18:50', '2025-10-27 15:18:50'),
(29, 47, 'Interface', 'Details', 'Added', 'Source System', NULL, 'Core System1', 'Alice Smith', '2025-10-27 15:18:50', '2025-10-27 15:18:50'),
(29, 48, 'Interface', 'Details', 'Added', 'Target System', NULL, 'IT System', 'Alice Smith', '2025-10-27 15:18:50', '2025-10-27 15:18:50'),
(29, 49, 'Interface', 'Details', 'Added', 'Automation', NULL, 'Automated', 'Alice Smith', '2025-10-27 15:18:50', '2025-10-27 15:18:50'),
(29, 50, 'Interface', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-10-27 15:18:50', '2025-10-27 15:18:50'),
(29, 51, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Change Request OwnerR', 'Alice Smith', '2025-10-27 15:18:50', '2025-10-27 15:18:50'),
(29, 52, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-27 15:18:50', '2025-10-27 15:18:50'),
(29, 53, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-27 15:18:50', '2025-10-27 15:18:50'),
(30, 54, 'Interface', 'Details', 'Added', 'Name', NULL, 'sdfsdfsd', 'Alice Smith', '2025-10-27 15:26:47', '2025-10-27 15:26:47'),
(30, 55, 'Interface', 'Details', 'Added', 'Reference Number', NULL, 'IF061', 'Alice Smith', '2025-10-27 15:26:47', '2025-10-27 15:26:47'),
(30, 56, 'Interface', 'Details', 'Added', 'Description', NULL, 'fsdfsdf', 'Alice Smith', '2025-10-27 15:26:47', '2025-10-27 15:26:47'),
(30, 57, 'Interface', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-27 15:26:47', '2025-10-27 15:26:47'),
(30, 58, 'Interface', 'Details', 'Added', 'Lifecycle', NULL, 'Production', 'Alice Smith', '2025-10-27 15:26:47', '2025-10-27 15:26:47'),
(30, 59, 'Interface', 'Details', 'Added', 'Source System', NULL, 'Operations System', 'Alice Smith', '2025-10-27 15:26:47', '2025-10-27 15:26:47'),
(30, 60, 'Interface', 'Details', 'Added', 'Target System', NULL, 'HR System1', 'Alice Smith', '2025-10-27 15:26:47', '2025-10-27 15:26:47'),
(30, 61, 'Interface', 'Details', 'Added', 'Automation', NULL, 'Automated', 'Alice Smith', '2025-10-27 15:26:47', '2025-10-27 15:26:47'),
(30, 62, 'Interface', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-10-27 15:26:47', '2025-10-27 15:26:47'),
(30, 63, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Change Request OwnerR', 'Alice Smith', '2025-10-27 15:26:47', '2025-10-27 15:26:47'),
(30, 64, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-27 15:26:47', '2025-10-27 15:26:47'),
(30, 65, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-27 15:26:47', '2025-10-27 15:26:47'),
(30, 66, 'Interface', 'Details', 'Updated', 'Name', 'sdfsdfsd', 'sdfsdfsd111', 'Alice Smith', '2025-10-27 15:34:50', '2025-10-27 15:34:50'),
(30, 67, 'Interface', 'Details', 'Updated', 'Name', 'sdfsdfsd111', 'sdfsdf', 'Alice Smith', '2025-10-27 15:35:24', '2025-10-27 15:35:24'),
(30, 68, 'Interface', 'Details', 'Updated', 'Automation', 'Automated', 'Semi-Automated', 'Alice Smith', '2025-10-27 15:36:06', '2025-10-27 15:36:06'),
(31, 69, 'Interface', 'Details', 'Added', 'Name', NULL, 'ddfvd', 'Alice Smith', '2025-10-27 15:57:25', '2025-10-27 15:57:25'),
(31, 70, 'Interface', 'Details', 'Added', 'Reference Number', NULL, 'IF062', 'Alice Smith', '2025-10-27 15:57:25', '2025-10-27 15:57:25'),
(31, 71, 'Interface', 'Details', 'Added', 'Description', NULL, 'sdvd', 'Alice Smith', '2025-10-27 15:57:25', '2025-10-27 15:57:25'),
(31, 72, 'Interface', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-27 15:57:25', '2025-10-27 15:57:25'),
(31, 73, 'Interface', 'Details', 'Added', 'Lifecycle', NULL, 'Production', 'Alice Smith', '2025-10-27 15:57:25', '2025-10-27 15:57:25'),
(31, 74, 'Interface', 'Details', 'Added', 'Source System', NULL, 'Core System1', 'Alice Smith', '2025-10-27 15:57:25', '2025-10-27 15:57:25'),
(31, 75, 'Interface', 'Details', 'Added', 'Target System', NULL, 'HR System1', 'Alice Smith', '2025-10-27 15:57:25', '2025-10-27 15:57:25'),
(31, 76, 'Interface', 'Details', 'Added', 'Automation', NULL, 'Automated', 'Alice Smith', '2025-10-27 15:57:25', '2025-10-27 15:57:25'),
(31, 77, 'Interface', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-10-27 15:57:25', '2025-10-27 15:57:25'),
(31, 78, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Change Request OwnerR', 'Alice Smith', '2025-10-27 15:57:25', '2025-10-27 15:57:25'),
(31, 79, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-27 15:57:25', '2025-10-27 15:57:25'),
(31, 80, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-27 15:57:25', '2025-10-27 15:57:25'),
(31, 81, 'Interface', 'Details', 'Updated', 'Name', 'ddfvd', 'ddfvdcc', 'Alice Smith', '2025-10-27 15:57:41', '2025-10-27 15:57:41'),
(31, 82, 'Interface', 'Details', 'Status Change', 'Status', 'Active', 'Inactive', 'admin admin', '2025-10-30 16:17:05', '2025-10-30 16:17:05'),
(32, 83, 'Interface', 'Details', 'Added', 'Name', NULL, 'int test 4', 'Alice Smith', '2025-11-05 16:04:32', '2025-11-05 16:04:32'),
(32, 84, 'Interface', 'Details', 'Added', 'Reference Number', NULL, 'IF063', 'Alice Smith', '2025-11-05 16:04:32', '2025-11-05 16:04:32'),
(32, 85, 'Interface', 'Details', 'Added', 'Description', NULL, '5/11', 'Alice Smith', '2025-11-05 16:04:32', '2025-11-05 16:04:32'),
(32, 86, 'Interface', 'Details', 'Added', 'Classification', NULL, 'Partner', 'Alice Smith', '2025-11-05 16:04:32', '2025-11-05 16:04:32'),
(32, 87, 'Interface', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-11-05 16:04:32', '2025-11-05 16:04:32'),
(32, 88, 'Interface', 'Details', 'Added', 'Lifecycle', NULL, 'Production', 'Alice Smith', '2025-11-05 16:04:32', '2025-11-05 16:04:32'),
(32, 89, 'Interface', 'Details', 'Added', 'Source System', NULL, 'system test 4', 'Alice Smith', '2025-11-05 16:04:32', '2025-11-05 16:04:32'),
(32, 90, 'Interface', 'Details', 'Added', 'Target System', NULL, 'sys_test', 'Alice Smith', '2025-11-05 16:04:32', '2025-11-05 16:04:32'),
(32, 91, 'Interface', 'Details', 'Added', 'Automation', NULL, 'Semi-Automated', 'Alice Smith', '2025-11-05 16:04:32', '2025-11-05 16:04:32'),
(32, 92, 'Interface', 'Details', 'Added', 'Frequency', NULL, 'Hourly', 'Alice Smith', '2025-11-05 16:04:32', '2025-11-05 16:04:32'),
(32, 93, 'Interface', 'Details', 'Added', 'Transfer Method', NULL, 'API', 'Alice Smith', '2025-11-05 16:04:32', '2025-11-05 16:04:32'),
(32, 94, 'Interface', 'Details', 'Added', 'Transfer Format', NULL, 'CSV', 'Alice Smith', '2025-11-05 16:04:32', '2025-11-05 16:04:32'),
(32, 95, 'Interface', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-11-05 16:04:32', '2025-11-05 16:04:32'),
(32, 96, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Interface Owner', 'Alice Smith', '2025-11-05 16:04:32', '2025-11-05 16:04:32'),
(32, 97, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-11-05 16:04:32', '2025-11-05 16:04:32'),
(32, 98, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-11-05 16:04:32', '2025-11-05 16:04:32'),
(32, 99, 'Interface', 'Details', 'Updated', 'Description', '5/11', '5///11', 'Alice Smith', '2025-11-05 16:07:44', '2025-11-05 16:07:44'),
(32, 100, 'Interface', 'Details', 'Updated', 'Classification', 'Partner', NULL, 'Alice Smith', '2025-11-05 16:07:44', '2025-11-05 16:07:44'),
(32, 101, 'Interface', 'Details', 'Updated', 'Lifecycle', 'Production', 'Testing', 'Alice Smith', '2025-11-05 16:07:44', '2025-11-05 16:07:44'),
(32, 102, 'Interface', 'Details', 'Updated', 'Frequency', 'Hourly', NULL, 'Alice Smith', '2025-11-05 16:07:44', '2025-11-05 16:07:44'),
(32, 103, 'Interface', 'Details', 'Updated', 'Transfer Method', 'API', NULL, 'Alice Smith', '2025-11-05 16:07:44', '2025-11-05 16:07:44'),
(32, 104, 'Interface', 'Details', 'Updated', 'Transfer Format', 'CSV', NULL, 'Alice Smith', '2025-11-05 16:07:44', '2025-11-05 16:07:44'),
(32, 122, 'Interface', 'Details', 'Updated', 'Name', 'int test 4', 'int test4', 'Alice Smith', '2025-11-09 12:15:03', '2025-11-09 12:15:03'),
(32, 123, 'Interface', 'Details', 'Updated', 'Lifecycle', 'Testing', 'Deprecated', 'Alice Smith', '2025-11-09 12:15:03', '2025-11-09 12:15:03'),
(32, 124, 'Interface', 'Details', 'Updated', 'Automation', 'Semi-Automated', 'Automated', 'Alice Smith', '2025-11-09 12:15:03', '2025-11-09 12:15:03'),
(32, 125, 'Interface', 'Details', 'Updated', 'Frequency', NULL, 'Real-time', 'Alice Smith', '2025-11-09 12:15:03', '2025-11-09 12:15:03'),
(32, 126, 'Interface', 'Details', 'Updated', 'Name', 'int test4', 'int test44', 'Alice Smith', '2025-11-09 12:18:12', '2025-11-09 12:18:12'),
(32, 127, 'Interface', 'Details', 'Updated', 'Lifecycle', 'Deprecated', 'Development', 'Alice Smith', '2025-11-09 12:18:12', '2025-11-09 12:18:12'),
(32, 128, 'Interface', 'Details', 'Updated', 'Lifecycle', 'Development', 'Deprecated', 'Alice Smith', '2025-11-09 12:18:55', '2025-11-09 12:18:55'),
(32, 129, 'Interface', 'Details', 'Updated', 'Frequency', 'Real-time', NULL, 'Alice Smith', '2025-11-09 12:18:55', '2025-11-09 12:18:55'),
(32, 130, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Eve Davis', 'System', '2025-11-09 12:18:55', '2025-11-09 12:18:55'),
(32, 131, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Interface Steward', 'System', '2025-11-09 12:18:55', '2025-11-09 12:18:55'),
(32, 132, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'System', '2025-11-09 12:18:55', '2025-11-09 12:18:55'),
(33, 105, 'Interface', 'Details', 'Added', 'Name', NULL, 'interface test 4', 'Alice Smith', '2025-11-09 10:54:06', '2025-11-09 10:54:06'),
(33, 106, 'Interface', 'Details', 'Added', 'Reference Number', NULL, 'IF064', 'Alice Smith', '2025-11-09 10:54:06', '2025-11-09 10:54:06'),
(33, 107, 'Interface', 'Details', 'Added', 'Description', NULL, '9/11', 'Alice Smith', '2025-11-09 10:54:06', '2025-11-09 10:54:06'),
(33, 108, 'Interface', 'Details', 'Added', 'Classification', NULL, 'External', 'Alice Smith', '2025-11-09 10:54:06', '2025-11-09 10:54:06'),
(33, 109, 'Interface', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-11-09 10:54:06', '2025-11-09 10:54:06'),
(33, 110, 'Interface', 'Details', 'Added', 'Lifecycle', NULL, 'Production', 'Alice Smith', '2025-11-09 10:54:06', '2025-11-09 10:54:06'),
(33, 111, 'Interface', 'Details', 'Added', 'Source System', NULL, 'system_test4', 'Alice Smith', '2025-11-09 10:54:06', '2025-11-09 10:54:06'),
(33, 112, 'Interface', 'Details', 'Added', 'Target System', NULL, 'Core System1', 'Alice Smith', '2025-11-09 10:54:06', '2025-11-09 10:54:06'),
(33, 113, 'Interface', 'Details', 'Added', 'Automation', NULL, 'Automated', 'Alice Smith', '2025-11-09 10:54:06', '2025-11-09 10:54:06'),
(33, 114, 'Interface', 'Details', 'Added', 'Frequency', NULL, 'Hourly', 'Alice Smith', '2025-11-09 10:54:06', '2025-11-09 10:54:06'),
(33, 115, 'Interface', 'Details', 'Added', 'Transfer Method', NULL, 'API', 'Alice Smith', '2025-11-09 10:54:06', '2025-11-09 10:54:06'),
(33, 116, 'Interface', 'Details', 'Added', 'Transfer Format', NULL, 'CSV', 'Alice Smith', '2025-11-09 10:54:06', '2025-11-09 10:54:06'),
(33, 117, 'Interface', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-11-09 10:54:06', '2025-11-09 10:54:06'),
(33, 118, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Interface Owner', 'Alice Smith', '2025-11-09 10:54:06', '2025-11-09 10:54:06'),
(33, 119, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-11-09 10:54:06', '2025-11-09 10:54:06'),
(33, 120, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-11-09 10:54:06', '2025-11-09 10:54:06'),
(33, 121, 'Interface', 'Details', 'Updated', 'Target System', 'Core System1', 'system test 4', 'Alice Smith', '2025-11-09 11:29:40', '2025-11-09 11:29:40');

--
-- Indexes for dumped tables
--

--
-- Indexes for table `interface_audit_history`
--
ALTER TABLE `interface_audit_history`
  ADD PRIMARY KEY (`id`,`auditidpk`),
  ADD UNIQUE KEY `auditidpk` (`auditidpk`);

--
-- AUTO_INCREMENT for dumped tables
--

--
-- AUTO_INCREMENT for table `interface_audit_history`
--
ALTER TABLE `interface_audit_history`
  MODIFY `auditidpk` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=134;

--
-- Constraints for dumped tables
--

--
-- Constraints for table `interface_audit_history`
--
ALTER TABLE `interface_audit_history`
  ADD CONSTRAINT `interface_Audit_history_ibfk_1` FOREIGN KEY (`id`) REFERENCES `interface` (`id`);
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
